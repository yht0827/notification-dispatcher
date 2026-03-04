package com.example.infrastructure.messaging.payload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotificationMessagePayload {
	private Long notificationId;
	private int retryCount;

	public NotificationMessagePayload(Long notificationId) {
		this(notificationId, 0);
	}

	public NotificationMessagePayload(Long notificationId, int retryCount) {
		this.notificationId = notificationId;
		this.retryCount = retryCount;
	}
}
