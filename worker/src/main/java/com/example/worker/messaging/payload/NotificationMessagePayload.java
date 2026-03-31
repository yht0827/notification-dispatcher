package com.example.worker.messaging.payload;

public record NotificationMessagePayload(Long notificationId, int retryCount) {

	public NotificationMessagePayload(Long notificationId) {
		this(notificationId, 0);
	}
}
