package com.example.worker.messaging.inbound;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;

import com.example.worker.messaging.payload.NotificationMessagePayload;

public record MessageProcessContext(
	NotificationMessagePayload payload,
	String sourceRecordId,
	long deliveryTag,
	String invalidReason
) {

	public static MessageProcessContext valid(NotificationMessagePayload payload, String sourceRecordId,
		long deliveryTag) {
		return new MessageProcessContext(payload, sourceRecordId, deliveryTag, null);
	}

	public static MessageProcessContext invalid(String sourceRecordId, long deliveryTag, String invalidReason) {
		return new MessageProcessContext(null, sourceRecordId, deliveryTag, invalidReason);
	}

	public boolean isInvalid() {
		return invalidReason != null;
	}

	public static MessageProcessContext fromAmqpMessage(Message message, MessageConverter messageConverter) {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		String sourceRecordId = resolveSourceRecordId(message, deliveryTag);
		try {
			Object converted = messageConverter.fromMessage(message);
			if (converted instanceof NotificationMessagePayload payload) {
				return valid(payload, sourceRecordId, deliveryTag);
			}
			return invalid(sourceRecordId, deliveryTag, "메시지 payload 변환 실패");
		} catch (RuntimeException exception) {
			return invalid(sourceRecordId, deliveryTag,
				"메시지 payload 변환 실패: " + exception.getMessage());
		}
	}

	private static String resolveSourceRecordId(Message message, long deliveryTag) {
		if (message != null && message.getMessageProperties() != null) {
			String messageId = message.getMessageProperties().getMessageId();
			if (messageId != null && !messageId.isBlank()) {
				return messageId;
			}
		}
		return String.valueOf(deliveryTag);
	}
}
