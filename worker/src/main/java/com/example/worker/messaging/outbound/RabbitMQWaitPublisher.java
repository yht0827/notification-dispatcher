package com.example.worker.messaging.outbound;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.worker.config.rabbitmq.NotificationRabbitProperties;
import com.example.worker.messaging.payload.NotificationWaitPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQWaitPublisher implements WaitPublisher {

	private static final int MAX_RETRY_BACKOFF_SHIFT = 10;

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(Long notificationId, int retryCount, String lastError, Long retryDelayMillis) {
		NotificationWaitPayload payload = NotificationWaitPayload.from(
			notificationId,
			retryCount,
			calculateDelayMillis(retryCount, retryDelayMillis),
			lastError
		);

		rabbitTemplate.convertAndSend(
			properties.waitExchange(),
			properties.waitQueue(),
			payload.toMessagePayload(),
			message -> {
				message.getMessageProperties().setExpiration(String.valueOf(payload.delayMillis()));
				return message;
			}
		);

		log.info("WAIT 큐 발행: notificationId={}, nextRetryCount={}, delayMs={}, reason={}",
			payload.notificationId(), payload.nextRetryCount(), payload.delayMillis(), payload.lastError());
	}

	private long calculateDelayMillis(int retryCount, Long override) {
		if (override != null && override > 0) {
			return override;
		}
		int capped = Math.clamp(retryCount, 0, MAX_RETRY_BACKOFF_SHIFT);
		long base = (long)properties.resolveRetryBaseDelayMillis() * (1L << capped);
		return applyJitter(base);
	}

	private long applyJitter(long delayMillis) {
		double jitterFactor = properties.resolveRetryJitterFactor();
		if (jitterFactor <= 0.0d) {
			return delayMillis;
		}
		double min = Math.max(0.0d, 1.0d - jitterFactor);
		double max = 1.0d + jitterFactor;
		double multiplier = ThreadLocalRandom.current().nextDouble(min, max);
		return Math.max(1L, Math.round(delayMillis * multiplier));
	}
}
