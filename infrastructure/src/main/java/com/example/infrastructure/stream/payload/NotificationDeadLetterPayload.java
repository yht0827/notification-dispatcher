package com.example.infrastructure.stream.payload;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.data.redis.connection.stream.RecordId;

public record NotificationDeadLetterPayload(
	String recordId,
	String notificationId,
	String payload,
	String reason,
	String failedAt
) {

	public static NotificationDeadLetterPayload from(RecordId sourceRecordId, Object payload, Long notificationId,
		String reason) {
		return new NotificationDeadLetterPayload(
			sourceRecordId.getValue(),
			toNotificationId(notificationId),
			Objects.toString(payload, ""),
			nullToEmpty(reason),
			OffsetDateTime.now().toString()
		);
	}

	private static String toNotificationId(Long notificationId) {
		return Objects.toString(notificationId, "");
	}

	private static String nullToEmpty(String value) {
		return Objects.requireNonNullElse(value, "");
	}
}
