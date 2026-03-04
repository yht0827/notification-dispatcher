package com.example.infrastructure.stream.outbound;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;
import com.example.infrastructure.stream.port.WaitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQWaitPublisher implements WaitPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final NotificationRabbitProperties properties;

	@Override
	public void publish(Long notificationId, int retryCount, String lastError) {
		long delayMillis = properties.calculateRetryDelayMillis(retryCount);
		int nextRetryCount = retryCount + 1;

		NotificationStreamPayload payload = new NotificationStreamPayload(notificationId, nextRetryCount);

		MessageConverter converter = rabbitTemplate.getMessageConverter();
		MessageProperties props = new MessageProperties();
		props.setExpiration(String.valueOf(delayMillis));
		Message message = converter.toMessage(payload, props);

		rabbitTemplate.send(properties.waitQueue(), message);

		log.info("WAIT 큐 발행: notificationId={}, nextRetryCount={}, delayMs={}, reason={}",
			notificationId, nextRetryCount, delayMillis, lastError);
	}
}
