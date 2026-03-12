package com.example.infrastructure.messaging.inbound;

/**
 * 재시도 대기 Queue 발행 인터페이스.
 * 일시적 오류로 실패한 메시지를 재시도 대기열로 전송합니다.
 */
public interface WaitPublisher {

	/**
	 * 재시도가 필요한 메시지를 대기 Queue로 발행합니다.
	 *
	 * @param notificationId 알림 ID
	 * @param retryCount 현재까지 재시도 횟수
	 * @param lastError 마지막 오류 메시지
	 */
	default void publish(Long notificationId, int retryCount, String lastError) {
		publish(notificationId, retryCount, lastError, null);
	}

	void publish(Long notificationId, int retryCount, String lastError, Long retryDelayMillis);
}
