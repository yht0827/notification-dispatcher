package com.example.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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

import com.example.infrastructure.messaging.inbound.RabbitMQBatchConsumer;
import com.example.infrastructure.messaging.inbound.RabbitMQRecordHandler;
import com.example.infrastructure.messaging.inbound.RecordProcessRequest;
import com.example.infrastructure.messaging.inbound.RecordProcessResult;
import com.example.infrastructure.messaging.inbound.DeadLetterPublisher;
import com.example.infrastructure.messaging.inbound.WaitPublisher;
import com.rabbitmq.client.Channel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@ExtendWith(MockitoExtension.class)
class RabbitMQBatchConsumerTest {

	@Mock
	private RabbitMQRecordHandler recordHandler;

	@Mock
	private DeadLetterPublisher dlqPublisher;

	@Mock
	private WaitPublisher waitPublisher;

	@Mock
	private MeterRegistry meterRegistry;

	@Mock
	private Counter counter;

	@Mock
	private MessageConverter messageConverter;

	@Mock
	private Channel channel;

	private RabbitMQBatchConsumer consumer;

	@BeforeEach
	void setUp() {
		when(meterRegistry.counter(any(), any(String[].class))).thenReturn(counter);
		consumer = new RabbitMQBatchConsumer(recordHandler, dlqPublisher, waitPublisher, meterRegistry,
			messageConverter);
	}

	@Test
	@DisplayName("배치 처리 성공 시 모든 메시지를 ACK 한다")
	void onMessages_acknowledgesAllWhenSuccessful() throws IOException {
		Message first = message(1L, "msg-1");
		Message second = message(2L, "msg-2");
		when(messageConverter.fromMessage(any())).thenReturn(payloadOf(1L), payloadOf(2L));
		when(recordHandler.processBatch(anyList())).thenReturn(List.of(
			RecordProcessResult.success(1L, 1L, 0),
			RecordProcessResult.success(2L, 2L, 0)
		));

		consumer.onMessages(List.of(first, second), channel);

		verify(channel).basicAck(1L, false);
		verify(channel).basicAck(2L, false);
	}

	@Test
	@DisplayName("배치 처리 중 예외 발생 시 해당 메시지를 NACK 한다")
	void onMessages_nacksWhenRecordHandlerThrows() throws IOException {
		Message first = message(5L, "msg-5");
		when(messageConverter.fromMessage(any())).thenReturn(payloadOf(10L));
		when(recordHandler.processBatch(anyList())).thenThrow(new RuntimeException("unexpected"));

		consumer.onMessages(List.of(first), channel);

		verify(channel).basicNack(5L, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	@DisplayName("non-retryable 실패 시 DLQ로 발행하고 ACK 한다")
	void onMessages_publishesToDlqOnNonRetryableFailure() throws IOException {
		Message msg = message(3L, "msg-3");
		when(messageConverter.fromMessage(any())).thenReturn(payloadOf(30L));
		when(recordHandler.processBatch(anyList())).thenReturn(List.of(
			RecordProcessResult.nonRetryableFailure(3L, 30L, 0, "주소 오류")
		));

		consumer.onMessages(List.of(msg), channel);

		verify(dlqPublisher).publish(any(), any(), any(), any());
		verify(channel).basicAck(3L, false);
	}

	@Test
	@DisplayName("retryable 실패 시 Wait 큐로 발행하고 ACK 한다")
	void onMessages_publishesToWaitOnRetryableFailure() throws IOException {
		Message msg = message(4L, "msg-4");
		when(messageConverter.fromMessage(any())).thenReturn(payloadOf(40L));
		when(recordHandler.processBatch(anyList())).thenReturn(List.of(
			RecordProcessResult.retryableFailure(4L, 40L, 0, "일시적 오류", 5000L)
		));

		consumer.onMessages(List.of(msg), channel);

		verify(waitPublisher).publish(any(), anyInt(), any(), any());
		verify(channel).basicAck(4L, false);
	}

	private Message message(long deliveryTag, String messageId) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		properties.setMessageId(messageId);
		return new Message(new byte[0], properties);
	}

	private com.example.infrastructure.messaging.payload.NotificationMessagePayload payloadOf(Long notificationId) {
		com.example.infrastructure.messaging.payload.NotificationMessagePayload payload =
			new com.example.infrastructure.messaging.payload.NotificationMessagePayload();
		payload.setNotificationId(notificationId);
		payload.setRetryCount(0);
		return payload;
	}
}
