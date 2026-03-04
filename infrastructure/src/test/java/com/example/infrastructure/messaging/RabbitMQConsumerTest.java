package com.example.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.exception.NonRetryableMessageException;
import com.example.infrastructure.messaging.exception.RetryableMessageException;
import com.example.infrastructure.messaging.inbound.RabbitMQConsumer;
import com.example.infrastructure.messaging.inbound.RabbitMQRecordHandler;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;
import com.example.infrastructure.messaging.port.DeadLetterPublisher;
import com.example.infrastructure.messaging.port.WaitPublisher;
import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class RabbitMQConsumerTest {

	@Mock
	private RabbitMQRecordHandler recordHandler;

	@Mock
	private DeadLetterPublisher dlqPublisher;

	@Mock
	private WaitPublisher waitPublisher;

	@Mock
	private Channel channel;

	private RabbitMQConsumer consumer;

	@BeforeEach
	void setUp() {
		NotificationRabbitProperties properties = new NotificationRabbitProperties(
			"notification.work",
			"notification.work.exchange",
			"notification.wait",
			"notification.dlq",
			"notification.dlq.exchange",
			3, 5000, 1, 10
		);
		consumer = new RabbitMQConsumer(recordHandler, dlqPublisher, waitPublisher, properties);
	}

	private Message createMessage() {
		return new Message(new byte[0], new MessageProperties());
	}

	@Test
	@DisplayName("처리 성공 시 ACK만 수행한다")
	void onMessage_acknowledgesWhenProcessSucceeds() throws IOException {
		// given
		NotificationMessagePayload payload = new NotificationMessagePayload(20L, 0);

		// when
		consumer.onMessage(payload, createMessage(), channel, 1L);

		// then
		verify(recordHandler).process(20L, 0);
		verify(channel).basicAck(1L, false);
		verify(dlqPublisher, never()).publish(any(), any(), any(), anyString());
		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
	}

	@Test
	@DisplayName("처리 중 non-retryable 오류는 DLQ 전송 후 ACK 한다")
	void onMessage_sendsDlqAndAckWhenProcessFailsNonRetryable() throws IOException {
		// given
		NotificationMessagePayload payload = new NotificationMessagePayload(10L, 0);

		doThrow(new NonRetryableMessageException("max retry exceeded"))
			.when(recordHandler)
			.process(10L, 0);

		// when
		consumer.onMessage(payload, createMessage(), channel, 1L);

		// then
		verify(dlqPublisher).publish(isNull(), eq(payload), eq(10L), eq("max retry exceeded"));
		verify(channel).basicAck(1L, false);
	}

	@Test
	@DisplayName("처리 중 retryable 오류는 WAIT 큐로 전송 후 ACK 한다")
	void onMessage_sendsToWaitQueueWhenProcessFailsRetryable() throws IOException {
		// given
		NotificationMessagePayload payload = new NotificationMessagePayload(30L, 1);

		doThrow(new RetryableMessageException("일시적 오류"))
			.when(recordHandler)
			.process(30L, 1);

		// when
		consumer.onMessage(payload, createMessage(), channel, 2L);

		// then
		verify(waitPublisher).publish(30L, 1, "일시적 오류");
		verify(channel).basicAck(2L, false);
	}

	@Test
	@DisplayName("notificationId가 null이면 DLQ 전송 후 ACK 한다")
	void onMessage_sendsDlqAndAckWhenNotificationIdNull() throws IOException {
		// given
		NotificationMessagePayload payload = new NotificationMessagePayload();
		payload.setNotificationId(null);
		payload.setRetryCount(0);

		// when
		consumer.onMessage(payload, createMessage(), channel, 3L);

		// then
		verify(dlqPublisher).publish(
			isNull(),
			eq(payload),
			isNull(),
			contains("payload 또는 notificationId 값이 비어 있습니다")
		);
		verify(channel).basicAck(3L, false);
	}

	@Test
	@DisplayName("예상치 못한 예외는 NACK(requeue=false) 처리한다")
	void onMessage_nacksOnUnexpectedException() throws IOException {
		// given
		NotificationMessagePayload payload = new NotificationMessagePayload(60L, 0);

		doThrow(new IllegalStateException("unexpected failure"))
			.when(recordHandler)
			.process(60L, 0);

		// when
		consumer.onMessage(payload, createMessage(), channel, 5L);

		// then
		verify(channel).basicNack(5L, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
		verify(dlqPublisher, never()).publish(any(), any(), any(), anyString());
		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
	}

	@Test
	@DisplayName("payload가 null이면 DLQ 전송 후 ACK 한다")
	void onMessage_sendsDlqAndAckWhenPayloadNull() throws IOException {
		// when
		consumer.onMessage(null, createMessage(), channel, 4L);

		// then
		verify(dlqPublisher).publish(
			isNull(),
			isNull(),
			isNull(),
			contains("payload 또는 notificationId 값이 비어 있습니다")
		);
		verify(channel).basicAck(4L, false);
	}
}
