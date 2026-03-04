package com.example.infrastructure.messaging.payload;

import java.time.OffsetDateTime;
import java.util.Objects;

public record NotificationDeadLetterPayload(
	String recordId,
	String notificationId,
	String payload,
	String reason,
	String failedAt
) {
	private static final String DEFAULT_SOURCE_RECORD_ID = "n/a";
	private static final String DEFAULT_NOTIFICATION_ID = "unknown";

	public static NotificationDeadLetterPayload from(String sourceRecordId, Object payload, Long notificationId,
		String reason) {
		return new NotificationDeadLetterPayload(
			normalizeSourceRecordId(sourceRecordId),
			toNotificationId(notificationId),
			toPayload(payload),
			nullToEmpty(reason),
			OffsetDateTime.now().toString()
		);
	}

	private static String normalizeSourceRecordId(String sourceRecordId) {
		return sourceRecordId != null ? sourceRecordId : DEFAULT_SOURCE_RECORD_ID;
	}

	private static String toNotificationId(Long notificationId) {
		return notificationId != null ? String.valueOf(notificationId) : DEFAULT_NOTIFICATION_ID;
	}

	private static String toPayload(Object payload) {
		return Objects.toString(payload, "");
	}

	private static String nullToEmpty(String value) {
		return Objects.requireNonNullElse(value, "");
	}
}
