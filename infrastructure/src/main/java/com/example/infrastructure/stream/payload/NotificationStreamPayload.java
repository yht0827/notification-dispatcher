package com.example.infrastructure.stream.payload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotificationStreamPayload {

	public static final String FIELD_NOTIFICATION_ID = "notificationId";
	public static final String FIELD_RETRY_COUNT = "retryCount";

	private Long notificationId;
	private int retryCount;

	public NotificationStreamPayload(Long notificationId) {
		this(notificationId, 0);
	}

	public NotificationStreamPayload(Long notificationId, int retryCount) {
		this.notificationId = notificationId;
		this.retryCount = retryCount;
	}
}
