package com.example.infrastructure.stream.payload;

import java.time.OffsetDateTime;

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
			notificationId == null ? "" : String.valueOf(notificationId),
			String.valueOf(payload),
			reason == null ? "" : reason,
			OffsetDateTime.now().toString()
		);
	}
}
