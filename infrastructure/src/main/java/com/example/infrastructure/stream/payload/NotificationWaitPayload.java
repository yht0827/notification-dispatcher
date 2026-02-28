package com.example.infrastructure.stream.payload;

import java.util.Objects;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotificationWaitPayload {

	private Long notificationId;
	private int retryCount;
	private long nextRetryAt;
	private String lastError;

	public NotificationWaitPayload(Long notificationId, int retryCount, long nextRetryAt, String lastError) {
		this.notificationId = notificationId;
		this.retryCount = retryCount;
		this.nextRetryAt = nextRetryAt;
		this.lastError = Objects.requireNonNullElse(lastError, "");
	}

	public static NotificationWaitPayload of(Long notificationId, int retryCount, long nextRetryAt, String lastError) {
		return new NotificationWaitPayload(notificationId, retryCount, nextRetryAt, lastError);
	}
}
