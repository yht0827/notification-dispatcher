package com.example.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import com.example.infrastructure.messaging.inbound.MessageProcessDecision;
import com.example.infrastructure.messaging.inbound.MessageProcessOrchestrator;
import com.example.infrastructure.messaging.inbound.RabbitMQBatchConsumer;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class RabbitMQBatchConsumerTest {

	@Mock
	private MessageProcessOrchestrator orchestrator;

	@Mock
	private MessageConverter messageConverter;

	@Mock
	private Channel channel;

	private RabbitMQBatchConsumer consumer;

	@BeforeEach
	void setUp() {
		consumer = new RabbitMQBatchConsumer(orchestrator, messageConverter);
	}

	@Test
	@DisplayName("배치 처리 결과가 ACK면 각 메시지를 ACK 한다")
	void onMessages_acknowledgesEachMessageWhenSuccessful() throws IOException {
		Message first = message(1L, "msg-1");
		Message second = message(2L, "msg-2");
		when(orchestrator.processBatch(any())).thenReturn(List.of(
			MessageProcessDecision.ack(1L),
			MessageProcessDecision.ack(2L)
		));

		consumer.onMessages(List.of(first, second), channel);

		verify(channel).basicAck(1L, false);
		verify(channel).basicAck(2L, false);
	}

	@Test
	@DisplayName("배치 처리 결과가 NACK면 각 메시지를 NACK 한다")
	void onMessages_nacksWhenUnexpectedExceptionOccurs() throws IOException {
		Message first = message(5L, null);
		Message second = message(6L, null);
		when(orchestrator.processBatch(any())).thenReturn(List.of(
			MessageProcessDecision.nack(5L),
			MessageProcessDecision.ack(6L)
		));

		consumer.onMessages(List.of(first, second), channel);

		verify(channel).basicNack(5L, false, false);
		verify(channel).basicAck(6L, false);
		verify(channel, never()).basicAck(5L, false);
	}

	private Message message(long deliveryTag, String messageId) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		properties.setMessageId(messageId);
		return new Message(new byte[0], properties);
	}
}
