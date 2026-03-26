package com.example.worker.messaging.outbound;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.worker.config.rabbitmq.NotificationRabbitProperties;
import com.example.worker.messaging.payload.NotificationDeadLetterPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQDlqPublisher implements DeadLetterPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(String sourceRecordId, Object payload, Long notificationId, String reason) {
		NotificationDeadLetterPayload dlqPayload = NotificationDeadLetterPayload.from(
			sourceRecordId,
			payload,
			notificationId,
			reason
		);

		rabbitTemplate.convertAndSend(
			properties.dlqExchange(),
			"",
			dlqPayload);
		log.warn("DLQ 발행: sourceRecordId={}, notificationId={}, reason={}",
			dlqPayload.recordId(), dlqPayload.notificationId(), dlqPayload.reason());
	}
}
