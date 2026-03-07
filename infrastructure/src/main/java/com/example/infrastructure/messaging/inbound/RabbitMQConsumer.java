package com.example.infrastructure.messaging.inbound;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import com.example.infrastructure.config.rabbitmq.RabbitBeanNames;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RabbitMQConsumer {

	private final MessageProcessOrchestrator orchestrator;

	@RabbitListener(
		queues = "${notification.rabbitmq.work-queue}",
		containerFactory = RabbitBeanNames.LISTENER_CONTAINER_FACTORY
	)
	public void onMessage(NotificationMessagePayload payload, Message message, Channel channel,
		@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		MessageProcessDecision decision = orchestrator.process(
			MessageProcessContext.fromTypedPayload(payload, message, deliveryTag)
		);
		if (decision.shouldAck()) {
			channel.basicAck(deliveryTag, false);
			return;
		}
		channel.basicNack(deliveryTag, false, false);
	}
}
