package com.example.worker.messaging.outbound;

public interface WaitPublisher {

	default void publish(Long notificationId, int retryCount, String lastError) {
		publish(notificationId, retryCount, lastError, null);
	}

	void publish(Long notificationId, int retryCount, String lastError, Long retryDelayMillis);
}
