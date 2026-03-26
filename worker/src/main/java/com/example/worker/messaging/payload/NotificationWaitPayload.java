package com.example.worker.messaging.payload;

import java.util.Objects;

public record NotificationWaitPayload(
	Long notificationId,
	int currentRetryCount,
	int nextRetryCount,
	long delayMillis,
	String lastError
) {

	public static NotificationWaitPayload from(Long notificationId, int retryCount, long delayMillis,
		String lastError) {
		int normalizedRetryCount = Math.max(retryCount, 0);
		return new NotificationWaitPayload(
			notificationId,
			normalizedRetryCount,
			normalizedRetryCount + 1,
			delayMillis,
			Objects.requireNonNullElse(lastError, "")
		);
	}

	public NotificationMessagePayload toMessagePayload() {
		return new NotificationMessagePayload(notificationId, nextRetryCount);
	}
}
