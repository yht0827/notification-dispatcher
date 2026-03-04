package com.example.infrastructure.messaging.outbound;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.payload.NotificationWaitPayload;
import com.example.infrastructure.messaging.port.WaitPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQWaitPublisher implements WaitPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(Long notificationId, int retryCount, String lastError) {
		// NotificationWaitPayload 생성
		NotificationWaitPayload payload = NotificationWaitPayload.from(
			notificationId,
			retryCount,
			// Delay(ms) = base_delay * 2^retryCount
			properties.calculateRetryDelayMillis(retryCount),
			lastError
		);

		// wait.exchange로 발행 (TTL 설정)
		rabbitTemplate.convertAndSend(
			properties.waitExchange(),
			properties.waitRoutingKey(),
			payload.toMessagePayload(),
			message -> {
				message.getMessageProperties().setExpiration(String.valueOf(payload.delayMillis()));
				return message;
			} // TTL 만료 → work.exchange → work.queue로 자동 라우팅
		);

		log.info("WAIT 큐 발행: notificationId={}, nextRetryCount={}, delayMs={}, reason={}",
			payload.notificationId(), payload.nextRetryCount(), payload.delayMillis(), payload.lastError());
	}
}
