package com.example.infrastructure.repository;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.example.application.port.out.DispatchLockManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchLockManagerImpl implements DispatchLockManager {

	private static final String KEY_PREFIX = "dispatch-lock:";
	private static final long LOCK_WAIT_TIME = 0;  // 대기 없이 즉시 반환
	private static final long LOCK_LEASE_TIME = 5; // 5분 후 자동 해제

	private final RedissonClient redissonClient;

	// 현재 스레드가 획득한 락 저장
	private final ThreadLocal<RLock> currentLock = new ThreadLocal<>();

	@Override
	public boolean tryAcquire(Long notificationId) {
		String key = buildKey(notificationId);
		RLock lock = redissonClient.getLock(key);

		try {
			boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.MINUTES);

			if (acquired) {
				currentLock.set(lock);
				log.debug("발송 락 획득: notificationId={}", notificationId);
				return true;
			}

			log.debug("발송 락 획득 실패 (이미 처리 중): notificationId={}", notificationId);
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("락 획득 중 인터럽트: notificationId={}", notificationId);
			return false;
		}
	}

	@Override
	public void release(Long notificationId) {
		RLock lock = currentLock.get();

		if (lock == null) {
			log.warn("해제할 락 없음: notificationId={}", notificationId);
			return;
		}

		try {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
				log.debug("발송 락 해제: notificationId={}", notificationId);
			} else {
				log.warn("현재 스레드가 소유한 락이 아님: notificationId={}", notificationId);
			}
		} finally {
			currentLock.remove();
		}
	}

	private String buildKey(Long notificationId) {
		return KEY_PREFIX + notificationId;
	}
}
