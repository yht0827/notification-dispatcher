package com.example.infrastructure.stream.payload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotificationWaitPayload {

	private String notificationId;
	private int retryCount;
	private long nextRetryAt;
	private String lastError;

	public NotificationWaitPayload(String notificationId, int retryCount, long nextRetryAt, String lastError) {
		this.notificationId = notificationId;
		this.retryCount = retryCount;
		this.nextRetryAt = nextRetryAt;
		this.lastError = lastError;
	}

	public static NotificationWaitPayload of(Long notificationId, int retryCount, long nextRetryAt, String lastError) {
		return new NotificationWaitPayload(
			notificationId != null ? String.valueOf(notificationId) : null,
			retryCount,
			nextRetryAt,
			lastError != null ? lastError : ""
		);
	}

	public Long notificationIdAsLong() {
		if (notificationId == null || notificationId.isBlank()) {
			return null;
		}
		return Long.parseLong(notificationId);
	}
}
