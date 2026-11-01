package com.beachcheck.auth.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Repository
public class EmailJobStore {
  private final JdbcTemplate jdbc;
  private final long leaseSeconds;

  public EmailJobStore(
      JdbcTemplate jdbc, @Value("${app.email.jobs.lease-seconds:300}") long leaseSeconds) {
    Assert.isTrue(leaseSeconds >= 60, "Email lease must be at least 60 seconds");
    this.jdbc = jdbc;
    this.leaseSeconds = leaseSeconds;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID enqueue(String recipient, String payload, String tokenHash, String traceparent) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO email_jobs(id, recipient, payload, token_hash, producer_traceparent) VALUES (?, ?, ?, ?, ?)",
        id,
        recipient,
        payload,
        tokenHash,
        traceparent);
    return id;
  }

  /** 선점·횟수 증가·이력 생성은 한 트랜잭션이며 SMTP 호출 전에 커밋한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ClaimResult claim() {
    var candidates =
        jdbc.query(
            """
        SELECT id, recipient, payload, attempts, producer_traceparent,
          EXISTS (SELECT 1 FROM email_verification_tokens t WHERE t.token = token_hash
            AND t.used_at IS NULL AND t.expires_at > CURRENT_TIMESTAMP) AS valid_token
        FROM email_jobs
        WHERE status = 'PENDING'
          OR (status = 'RETRY_WAIT' AND next_retry_at <= CURRENT_TIMESTAMP)
          OR (status = 'SENDING' AND lease_until <= CURRENT_TIMESTAMP)
        ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
        """,
            (rs, row) ->
                new Candidate(
                    new Job(
                        rs.getObject("id", UUID.class),
                        rs.getString("recipient"),
                        rs.getString("payload"),
                        rs.getInt("attempts"),
                        rs.getString("producer_traceparent")),
                    rs.getBoolean("valid_token")));
    if (candidates.isEmpty()) return new ClaimResult(Optional.empty(), true);
    Candidate candidate = candidates.getFirst();
    Job old = candidate.job();
    jdbc.update(
        """
        UPDATE email_job_attempts SET outcome = 'UNKNOWN', finished_at = CURRENT_TIMESTAMP
        WHERE job_id = ? AND outcome = 'STARTED'
        """,
        old.id());
    if (old.attempt() >= 4 || !candidate.validToken()) {
      jdbc.update(
          """
          UPDATE email_jobs SET status = 'FAILED', payload = NULL, lease_until = NULL,
            next_retry_at = NULL, last_error = ? WHERE id = ?
          """,
          old.attempt() >= 4 ? "ATTEMPTS_EXHAUSTED_UNKNOWN" : "TOKEN_INACTIVE",
          old.id());
      // 정리한 후보도 이번 배치의 한 건으로 세되, 다음 후보를 계속 조회한다.
      return new ClaimResult(Optional.empty(), false);
    }
    int attempt = old.attempt() + 1;
    jdbc.update(
        """
        UPDATE email_jobs SET status = 'SENDING', attempts = ?, last_attempt_at = CURRENT_TIMESTAMP,
          next_retry_at = NULL, lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
        WHERE id = ?
        """,
        attempt,
        leaseSeconds,
        old.id());
    jdbc.update("INSERT INTO email_job_attempts(job_id, attempt) VALUES (?, ?)", old.id(), attempt);
    return new ClaimResult(
        Optional.of(new Job(old.id(), old.recipient(), old.payload(), attempt, old.traceparent())),
        false);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean finish(Job job, String status, String outcome, long delayMs) {
    int changed =
        jdbc.update(
            """
        UPDATE email_jobs SET status = ?, lease_until = NULL,
          next_retry_at = CASE WHEN ? = 'RETRY_WAIT' THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond') ELSE NULL END,
          payload = CASE WHEN ? IN ('SUCCEEDED', 'FAILED') AND ? <> 'PAYLOAD_DECRYPT_FAILED' THEN NULL ELSE payload END,
          last_error = CASE WHEN ? = 'SUCCEEDED' THEN NULL ELSE ? END
        WHERE id = ? AND status = 'SENDING' AND attempts = ?
        """,
            status,
            status,
            delayMs,
            status,
            outcome,
            status,
            outcome,
            job.id(),
            job.attempt());
    if (changed == 0) return false;
    jdbc.update(
        """
        UPDATE email_job_attempts SET outcome = ?, finished_at = CURRENT_TIMESTAMP
        WHERE job_id = ? AND attempt = ?
        """,
        outcome,
        job.id(),
        job.attempt());
    return true;
  }

  public record ClaimResult(Optional<Job> job, boolean queueEmpty) {}

  public record Job(UUID id, String recipient, String payload, int attempt, String traceparent) {}

  private record Candidate(Job job, boolean validToken) {}
}
