package com.example.infrastructure.lock;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.infrastructure.support.IntegrationTestSupportNoTx;

class DispatchLockManagerImplTest extends IntegrationTestSupportNoTx {

	@Autowired
	private DispatchLockManagerImpl lockManager;

	@Test
	@DisplayName("동일 notificationId에 대해 하나의 스레드만 락을 획득한다")
	void onlyOneThreadAcquiresLockForSameNotificationId() throws InterruptedException {
		// given
		Long notificationId = 1L;
		int threadCount = 10;
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(threadCount);
		AtomicInteger acquiredCount = new AtomicInteger(0);
		List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);

		// when
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					boolean acquired = lockManager.tryAcquire(notificationId);
					results.add(acquired);
					if (acquired) {
						acquiredCount.incrementAndGet();
						Thread.sleep(100);
						lockManager.release(notificationId);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					endLatch.countDown();
				}
			});
		}

		startLatch.countDown();
		endLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		// then
		assertThat(acquiredCount.get()).isEqualTo(1);
		assertThat(results.stream().filter(r -> r).count()).isEqualTo(1);
		assertThat(results.stream().filter(r -> !r).count()).isEqualTo(threadCount - 1);
	}

	@Test
	@DisplayName("락 해제 후 다른 스레드가 락을 획득할 수 있다")
	void canAcquireLockAfterRelease() {
		// given
		Long notificationId = 2L;

		// when & then - 첫 번째 스레드 락 획득 및 해제
		boolean firstAcquired = lockManager.tryAcquire(notificationId);
		assertThat(firstAcquired).isTrue();
		lockManager.release(notificationId);

		// when & then - 두 번째 스레드 락 획득 가능
		boolean secondAcquired = lockManager.tryAcquire(notificationId);
		assertThat(secondAcquired).isTrue();
		lockManager.release(notificationId);
	}

	@Test
	@DisplayName("락을 획득한 상태에서 다른 스레드는 획득 실패한다")
	void failsToAcquireWhenAlreadyLocked() throws InterruptedException, ExecutionException {
		Long notificationId = 3L;

		boolean firstAcquired = lockManager.tryAcquire(notificationId);
		assertThat(firstAcquired).isTrue();

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<Boolean> secondAttempt = executor.submit(() -> lockManager.tryAcquire(notificationId));
			boolean secondAcquired = secondAttempt.get();

			assertThat(secondAcquired).isFalse();
		} finally {
			lockManager.release(notificationId);
			executor.shutdownNow();
			executor.awaitTermination(3, TimeUnit.SECONDS);
		}
	}

	@Test
	@DisplayName("락을 획득하지 않은 상태에서 release 호출은 안전하게 무시된다")
	void releaseWithoutAcquireIsSafe() {
		// given
		Long notificationId = 4L;

		// when & then - 예외 없이 안전하게 처리
		lockManager.release(notificationId);
	}

	@Test
	@DisplayName("서로 다른 notificationId는 독립적으로 락을 획득할 수 있다")
	void differentNotificationIdsAreIndependent() {
		// given
		Long notificationId1 = 5L;
		Long notificationId2 = 6L;

		// when
		boolean acquired1 = lockManager.tryAcquire(notificationId1);
		boolean acquired2 = lockManager.tryAcquire(notificationId2);

		// then
		assertThat(acquired1).isTrue();
		assertThat(acquired2).isTrue();

		// cleanup
		lockManager.release(notificationId1);
		lockManager.release(notificationId2);
	}

	@Test
	@DisplayName("락 해제 후 ThreadLocal이 정리된다")
	void threadLocalIsClearedAfterRelease() {
		// given
		Long notificationId = 7L;

		// when
		boolean acquired = lockManager.tryAcquire(notificationId);
		assertThat(acquired).isTrue();
		lockManager.release(notificationId);

		// then - 같은 스레드에서 다시 획득 가능 (ThreadLocal이 정리되었으므로)
		boolean reacquired = lockManager.tryAcquire(notificationId);
		assertThat(reacquired).isTrue();
		lockManager.release(notificationId);
	}

	@Test
	@DisplayName("순차적으로 여러 스레드가 락을 획득하고 해제할 수 있다")
	void sequentialLockAcquisitionWorks() throws InterruptedException {
		// given
		Long notificationId = 8L;
		int threadCount = 5;
		AtomicInteger successCount = new AtomicInteger(0);

		// when - 순차적으로 락 획득/해제
		for (int i = 0; i < threadCount; i++) {
			Thread thread = new Thread(() -> {
				boolean acquired = lockManager.tryAcquire(notificationId);
				if (acquired) {
					successCount.incrementAndGet();
					lockManager.release(notificationId);
				}
			});
			thread.start();
			thread.join();
		}

		// then
		assertThat(successCount.get()).isEqualTo(threadCount);
	}
}
