package com.beachcheck.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.beachcheck.global.config.AsyncConfig;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class EmailJobSchedulerTest {
  @Test
  void repeatedTicksReserveAtMostThreeConsumersWithoutClaimingBeforeExecution() {
    var worker = mock(EmailJobWorker.class);
    var queued = new ArrayDeque<Runnable>();
    var scheduler = new EmailJobScheduler(worker, queued::add);
    scheduler.poll();
    scheduler.poll();
    assertThat(queued).hasSize(3);
    verifyNoInteractions(worker);
    queued.remove().run();
    verify(worker).poll();
    scheduler.poll();
    assertThat(queued).hasSize(3);
  }

  @Test
  void rejectedDispatchReleasesSlotForNextTick() {
    var worker = mock(EmailJobWorker.class);
    var queued = new ArrayDeque<Runnable>();
    var reject = new AtomicBoolean(true);
    var scheduler =
        new EmailJobScheduler(
            worker,
            task -> {
              if (reject.getAndSet(false)) throw new RejectedExecutionException("full");
              queued.add(task);
            });
    scheduler.poll();
    verifyNoInteractions(worker);
    scheduler.poll();
    assertThat(queued).hasSize(3);
  }

  @Test
  void failedConsumersReleaseTheirSlots() {
    var worker = mock(EmailJobWorker.class);
    doThrow(new IllegalStateException("failure")).when(worker).poll();
    var queued = new ArrayDeque<Runnable>();
    var scheduler = new EmailJobScheduler(worker, queued::add);
    scheduler.poll();
    for (int i = 0; i < 3; i++) queued.remove().run();
    scheduler.poll();
    assertThat(queued).hasSize(3);
    verify(worker, times(3)).poll();
  }

  @Test
  void slowConsumersUseExistingExecutorWithoutBlockingUnrelatedScheduledWork() throws Exception {
    var worker = mock(EmailJobWorker.class);
    var entered = new CountDownLatch(3);
    var release = new CountDownLatch(1);
    var finished = new CountDownLatch(3);
    doAnswer(
            call -> {
              entered.countDown();
              try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              } finally {
                finished.countDown();
              }
              return null;
            })
        .when(worker)
        .poll();
    var executor = (ThreadPoolTaskExecutor) new AsyncConfig().emailTaskExecutor(Tracer.NOOP);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(5);
    var sharedScheduler = new ThreadPoolTaskScheduler();
    sharedScheduler.setPoolSize(1);
    sharedScheduler.initialize();
    try {
      var scheduler = new EmailJobScheduler(worker, executor);
      sharedScheduler.schedule(scheduler::poll, Instant.now()).get(2, TimeUnit.SECONDS);
      assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
      var unrelated = new CountDownLatch(1);
      sharedScheduler.schedule(unrelated::countDown, Instant.now());
      assertThat(unrelated.await(2, TimeUnit.SECONDS)).isTrue();
      scheduler.poll();
      assertThat(executor.getThreadPoolExecutor().getQueue()).isEmpty();
      verify(worker, times(3)).poll();
    } finally {
      release.countDown();
      assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
      sharedScheduler.shutdown();
      executor.shutdown();
    }
  }
}
