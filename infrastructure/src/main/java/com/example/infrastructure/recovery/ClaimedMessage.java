package com.example.infrastructure.recovery;

import java.util.Map;
import java.util.Optional;

import com.example.infrastructure.stream.payload.NotificationStreamPayload;

import io.lettuce.core.StreamMessage;

record ClaimedMessage(String id, Map<String, String> body) {

	static ClaimedMessage from(StreamMessage<String, String> message) {
		return new ClaimedMessage(message.getId(), message.getBody());
	}

	Optional<Long> notificationId() {
		return Optional.ofNullable(body.get(NotificationStreamPayload.FIELD_NOTIFICATION_ID))
			.map(Long::parseLong);
	}

	int retryCount() {
		String value = body.get(NotificationStreamPayload.FIELD_RETRY_COUNT);
		return value != null ? Integer.parseInt(value) : 0;
	}
}
