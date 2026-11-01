package com.beachcheck.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class EmailJobSchedulerTest {
  @Test
  void emptyQueueRunsOneConsumerPerTickWithoutSpeculativeFanout() {
    var worker = mock(EmailJobWorker.class);
    var queued = new ArrayDeque<Runnable>();
    var scheduler = new EmailJobScheduler(worker, queued::add);
    scheduler.poll();
    assertThat(queued).hasSize(1);
    verifyNoInteractions(worker);
    queued.remove().run(); // 작업 발견 알림이 없으면 소비 작업을 늘리지 않는다.
    assertThat(queued).isEmpty();
    verify(worker).poll(any(Runnable.class));
    scheduler.poll();
    assertThat(queued).hasSize(1);
  }

  @Test
  void findingWorkExpandsConsumersButRepeatedTicksRemainBounded() {
    var worker = mock(EmailJobWorker.class);
    var queued = new ArrayDeque<Runnable>();
    doAnswer(
            call -> {
              call.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(worker)
        .poll(any(Runnable.class));
    var scheduler = new EmailJobScheduler(worker, queued::add);
    scheduler.poll();
    queued.remove().run();
    assertThat(queued).hasSize(2);
    scheduler.poll();
    scheduler.poll();
    assertThat(queued).hasSize(3);
    verify(worker).poll(any(Runnable.class));
  }

  @Test
  void rejectedDispatchReleasesSlotForNextTick() {
    var worker = mock(EmailJobWorker.class);
    var queued = new ArrayDeque<Runnable>();
    var rejections = new AtomicInteger(3);
    var scheduler =
        new EmailJobScheduler(
            worker,
            task -> {
              if (rejections.getAndDecrement() > 0) throw new RejectedExecutionException("full");
              queued.add(task);
            });
    for (int i = 0; i < 3; i++) scheduler.poll();
    verifyNoInteractions(worker);
    scheduler.poll();
    assertThat(queued).hasSize(1);
  }

  @Test
  void failedConsumersReleaseTheirSlots() {
    var worker = mock(EmailJobWorker.class);
    doThrow(new IllegalStateException("failure")).when(worker).poll(any(Runnable.class));
    var queued = new ArrayDeque<Runnable>();
    var scheduler = new EmailJobScheduler(worker, queued::add);
    for (int i = 0; i < 3; i++) {
      scheduler.poll();
      queued.remove().run();
    }
    scheduler.poll();
    assertThat(queued).hasSize(1);
    verify(worker, times(3)).poll(any(Runnable.class));
  }

  @Test
  void slowConsumersUseExistingExecutorWithoutBlockingUnrelatedScheduledWork() throws Exception {
    var worker = mock(EmailJobWorker.class);
    var entered = new CountDownLatch(3);
    var release = new CountDownLatch(1);
    var finished = new CountDownLatch(3);
    doAnswer(
            call -> {
              call.getArgument(0, Runnable.class).run();
              entered.countDown();
              try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              } finally {
                finished.countDown();
              }
              return null;
            })
        .when(worker)
        .poll(any(Runnable.class));
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
      verify(worker, times(3)).poll(any(Runnable.class));
    } finally {
      release.countDown();
      assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
      sharedScheduler.shutdown();
      executor.shutdown();
    }
  }
}
