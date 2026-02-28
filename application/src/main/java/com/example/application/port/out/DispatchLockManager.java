package com.example.application.port.out;

public interface DispatchLockManager {

	/**
	 * 발송 처리 락 획득 시도
	 * @return true = 락 획득 성공 (처리 가능), false = 이미 처리 중 (스킵)
	 */
	boolean tryAcquire(Long notificationId);

	/**
	 * 락 해제 (실패 시 재시도 가능하도록)
	 */
	void release(Long notificationId);
}
