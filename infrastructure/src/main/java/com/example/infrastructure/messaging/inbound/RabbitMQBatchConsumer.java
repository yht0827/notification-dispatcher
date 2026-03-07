package com.example.infrastructure.messaging.inbound;

import java.io.IOException;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;

import com.example.infrastructure.config.rabbitmq.RabbitBeanNames;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RabbitMQBatchConsumer {

	private final MessageProcessOrchestrator orchestrator;
	private final MessageConverter messageConverter;

	@RabbitListener(
		queues = "${notification.rabbitmq.work-queue}",
		containerFactory = RabbitBeanNames.BATCH_LISTENER_CONTAINER_FACTORY
	)
	public void onMessages(List<Message> messages, Channel channel) throws IOException {
		List<MessageProcessDecision> decisions = orchestrator.processBatch(
			messages.stream()
				.map(message -> MessageProcessContext.fromAmqpMessage(message, messageConverter))
				.toList()
		);
		for (MessageProcessDecision decision : decisions) {
			if (decision.shouldAck()) {
				channel.basicAck(decision.deliveryTag(), false);
				continue;
			}
			channel.basicNack(decision.deliveryTag(), false, false);
		}
	}
}
