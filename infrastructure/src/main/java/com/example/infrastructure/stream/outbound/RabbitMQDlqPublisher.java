package com.example.infrastructure.stream.outbound;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.stream.payload.NotificationDeadLetterPayload;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;
import com.example.infrastructure.stream.port.DeadLetterPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.OffsetDateTime;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQDlqPublisher implements DeadLetterPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(String sourceRecordId, Object payload, Long notificationId, String reason) {
		String notificationIdStr = notificationId != null ? String.valueOf(notificationId) : "unknown";
		String sourceId = sourceRecordId != null ? sourceRecordId : "n/a";

		NotificationDeadLetterPayload dlqPayload = new NotificationDeadLetterPayload(
			sourceId,
			notificationIdStr,
			payload != null ? payload.toString() : "null",
			reason,
			OffsetDateTime.now().toString()
		);

		rabbitTemplate.convertAndSend(properties.dlqExchange(), "", dlqPayload);
		log.warn("DLQ 발행: notificationId={}, reason={}", notificationId, reason);
	}
}
