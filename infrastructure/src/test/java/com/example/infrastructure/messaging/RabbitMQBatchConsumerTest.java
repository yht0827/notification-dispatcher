package com.example.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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

import com.example.infrastructure.messaging.exception.NonRetryableMessageException;
import com.example.infrastructure.messaging.exception.RetryableMessageException;
import com.example.infrastructure.messaging.inbound.RabbitMQBatchConsumer;
import com.example.infrastructure.messaging.inbound.RabbitMQRecordHandler;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;
import com.example.infrastructure.messaging.port.DeadLetterPublisher;
import com.example.infrastructure.messaging.port.WaitPublisher;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class RabbitMQBatchConsumerTest {

	@Mock
	private RabbitMQRecordHandler recordHandler;

	@Mock
	private DeadLetterPublisher dlqPublisher;

	@Mock
	private WaitPublisher waitPublisher;

	@Mock
	private MessageConverter messageConverter;

	@Mock
	private Channel channel;

	private RabbitMQBatchConsumer consumer;

	@BeforeEach
	void setUp() {
		consumer = new RabbitMQBatchConsumer(recordHandler, dlqPublisher, waitPublisher, messageConverter);
	}

	@Test
	@DisplayName("배치 처리 성공 시 각 메시지를 ACK 한다")
	void onMessages_acknowledgesEachMessageWhenSuccessful() throws IOException {
		Message first = message(1L, "msg-1");
		Message second = message(2L, "msg-2");
		when(messageConverter.fromMessage(first)).thenReturn(new NotificationMessagePayload(10L, 0));
		when(messageConverter.fromMessage(second)).thenReturn(new NotificationMessagePayload(20L, 1));

		consumer.onMessages(List.of(first, second), channel);

		verify(recordHandler).process(10L, 0);
		verify(recordHandler).process(20L, 1);
		verify(channel).basicAck(1L, false);
		verify(channel).basicAck(2L, false);
	}

	@Test
	@DisplayName("non-retryable 예외는 DLQ 전송 후 ACK 한다")
	void onMessages_sendsDlqAndAckWhenProcessFailsNonRetryable() throws IOException {
		Message message = message(3L, "source-3");
		NotificationMessagePayload payload = new NotificationMessagePayload(30L, 0);
		when(messageConverter.fromMessage(message)).thenReturn(payload);
		doThrow(new NonRetryableMessageException("bad payload"))
			.when(recordHandler)
			.process(30L, 0);

		consumer.onMessages(List.of(message), channel);

		verify(dlqPublisher).publish("source-3", payload, 30L, "bad payload");
		verify(channel).basicAck(3L, false);
		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
	}

	@Test
	@DisplayName("retryable 예외는 WAIT 큐 전송 후 ACK 한다")
	void onMessages_sendsToWaitAndAckWhenProcessFailsRetryable() throws IOException {
		Message message = message(4L, null);
		when(messageConverter.fromMessage(message)).thenReturn(new NotificationMessagePayload(40L, 2));
		doThrow(new RetryableMessageException("temporary"))
			.when(recordHandler)
			.process(40L, 2);

		consumer.onMessages(List.of(message), channel);

		verify(waitPublisher).publish(40L, 2, "temporary");
		verify(channel).basicAck(4L, false);
		verify(dlqPublisher, never()).publish(anyString(), any(), any(), anyString());
	}

	@Test
	@DisplayName("예상치 못한 예외는 NACK 처리한다")
	void onMessages_nacksWhenUnexpectedExceptionOccurs() throws IOException {
		Message message = message(5L, null);
		when(messageConverter.fromMessage(message)).thenReturn(new NotificationMessagePayload(50L, 0));
		doThrow(new IllegalStateException("boom"))
			.when(recordHandler)
			.process(50L, 0);

		consumer.onMessages(List.of(message), channel);

		verify(channel).basicNack(5L, false, false);
		verify(channel, never()).basicAck(5L, false);
	}

	@Test
	@DisplayName("payload의 notificationId가 없으면 DLQ 전송 후 ACK 한다")
	void onMessages_sendsDlqWhenNotificationIdMissing() throws IOException {
		Message message = message(6L, null);
		NotificationMessagePayload payload = new NotificationMessagePayload();
		payload.setRetryCount(0);
		when(messageConverter.fromMessage(message)).thenReturn(payload);

		consumer.onMessages(List.of(message), channel);

		verify(dlqPublisher).publish("6", payload, null, "payload 또는 notificationId 값이 비어 있습니다.");
		verify(channel).basicAck(6L, false);
	}

	@Test
	@DisplayName("메시지 변환 실패는 즉시 예외를 던지고 ACK 하지 않는다")
	void onMessages_throwsWhenConversionFails() throws IOException {
		Message message = message(7L, null);
		when(messageConverter.fromMessage(message)).thenReturn("not-a-payload");

		assertThatThrownBy(() -> consumer.onMessages(List.of(message), channel))
			.isInstanceOf(NonRetryableMessageException.class)
			.hasMessageContaining("메시지 payload 변환 실패");

		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	private Message message(long deliveryTag, String messageId) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		properties.setMessageId(messageId);
		return new Message(new byte[0], properties);
	}
}
