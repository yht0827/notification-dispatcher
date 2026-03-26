package com.example.worker.messaging.outbound;

public interface DeadLetterPublisher {

	void publish(String sourceRecordId, Object payload, Long notificationId, String reason);
}
