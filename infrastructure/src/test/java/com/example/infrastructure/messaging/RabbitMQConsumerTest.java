package com.example.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.example.infrastructure.messaging.inbound.MessageProcessDecision;
import com.example.infrastructure.messaging.inbound.MessageProcessOrchestrator;
import com.example.infrastructure.messaging.inbound.RabbitMQConsumer;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class RabbitMQConsumerTest {

	@Mock
	private MessageProcessOrchestrator orchestrator;

	@Mock
	private Channel channel;

	private RabbitMQConsumer consumer;

	@BeforeEach
	void setUp() {
		consumer = new RabbitMQConsumer(orchestrator);
	}

	private Message createMessage() {
		return new Message(new byte[0], new MessageProperties());
	}

	@Test
	@DisplayName("orchestrator가 ACK를 반환하면 ACK 한다")
	void onMessage_acknowledgesWhenProcessSucceeds() throws IOException {
		NotificationMessagePayload payload = new NotificationMessagePayload(20L, 0);
		when(orchestrator.process(any())).thenReturn(MessageProcessDecision.ack(1L));

		consumer.onMessage(payload, createMessage(), channel, 1L);

		verify(channel).basicAck(1L, false);
		verify(channel, never()).basicNack(1L, false, false);
	}

	@Test
	@DisplayName("orchestrator가 NACK를 반환하면 NACK 한다")
	void onMessage_nacksOnUnexpectedException() throws IOException {
		NotificationMessagePayload payload = new NotificationMessagePayload(60L, 0);
		when(orchestrator.process(any())).thenReturn(MessageProcessDecision.nack(5L));

		consumer.onMessage(payload, createMessage(), channel, 5L);

		verify(channel).basicNack(5L, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}
}
