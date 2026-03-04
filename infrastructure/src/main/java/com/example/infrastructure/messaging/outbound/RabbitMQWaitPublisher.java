package com.example.infrastructure.messaging.outbound;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.payload.NotificationWaitPayload;
import com.example.infrastructure.messaging.port.WaitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQWaitPublisher implements WaitPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(Long notificationId, int retryCount, String lastError) {
		NotificationWaitPayload payload = NotificationWaitPayload.from(
			notificationId,
			retryCount,
			properties.calculateRetryDelayMillis(retryCount),
			lastError
		);

		rabbitTemplate.convertAndSend(
			properties.waitExchange(),
			properties.waitRoutingKey(),
			payload.toMessagePayload(),
			message -> {
				message.getMessageProperties().setExpiration(String.valueOf(payload.delayMillis()));
				return message;
			}
		);

		log.info("WAIT 큐 발행: notificationId={}, nextRetryCount={}, delayMs={}, reason={}",
			payload.notificationId(), payload.nextRetryCount(), payload.delayMillis(), payload.lastError());
	}
}
