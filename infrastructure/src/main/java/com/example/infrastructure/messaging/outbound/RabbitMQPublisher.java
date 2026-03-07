package com.example.infrastructure.messaging.outbound;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQPublisher implements NotificationEventPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(Long notificationId) {
		// NotificationMessagePayload 생성
		NotificationMessagePayload payload = new NotificationMessagePayload(notificationId);

		// work.exchange로 발행
		rabbitTemplate.convertAndSend(properties.workExchange(), properties.workRoutingKey(), payload);
		log.info("RabbitMQ 발행: notificationId={}", notificationId);
	}
}
