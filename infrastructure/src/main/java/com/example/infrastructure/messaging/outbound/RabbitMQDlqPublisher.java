package com.example.infrastructure.messaging.outbound;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.payload.NotificationDeadLetterPayload;
import com.example.infrastructure.messaging.port.DeadLetterPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQDlqPublisher implements DeadLetterPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(String sourceRecordId, Object payload, Long notificationId, String reason) {
		// NotificationDeadLetterPayload 생성
		NotificationDeadLetterPayload dlqPayload = NotificationDeadLetterPayload.from(
			sourceRecordId,
			payload,
			notificationId,
			reason
		);

		// dlq.exchange (Fanout)로 발행
		rabbitTemplate.convertAndSend(
			properties.dlqExchange(),
			"", // 라우팅 키 무시 (Fanout)
			dlqPayload);
		log.warn("DLQ 발행: sourceRecordId={}, notificationId={}, reason={}",
			dlqPayload.recordId(), dlqPayload.notificationId(), dlqPayload.reason());
	}
}
