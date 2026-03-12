package com.example.infrastructure.messaging.inbound;

/**
 * Dead Letter Queue 발행 인터페이스.
 * 처리 불가능한 메시지를 DLQ로 전송합니다.
 */
public interface DeadLetterPublisher {

	/**
	 * 처리 실패한 메시지를 DLQ로 발행합니다.
	 *
	 * @param sourceRecordId 원본 메시지의 Record ID (nullable)
	 * @param payload 원본 페이로드
	 * @param notificationId 알림 ID
	 * @param reason 실패 사유
	 */
	void publish(String sourceRecordId, Object payload, Long notificationId, String reason);
}
