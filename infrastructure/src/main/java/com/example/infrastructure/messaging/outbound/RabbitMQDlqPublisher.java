package com.example.infrastructure.messaging.outbound;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.payload.NotificationDeadLetterPayload;
import com.example.infrastructure.messaging.port.DeadLetterPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

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

		rabbitTemplate.convertAndSend(properties.dlqExchange(), "", dlqPayload);
		log.warn("DLQ 발행: sourceRecordId={}, notificationId={}, reason={}",
			dlqPayload.recordId(), dlqPayload.notificationId(), dlqPayload.reason());
	}
}
