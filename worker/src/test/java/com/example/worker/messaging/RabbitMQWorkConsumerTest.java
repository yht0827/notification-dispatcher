package com.example.worker.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.springframework.amqp.support.converter.MessageConverter;

import com.example.worker.messaging.inbound.RabbitMQRecordHandler;
import com.example.worker.messaging.inbound.RabbitMQWorkConsumer;
import com.example.worker.messaging.inbound.RecordProcessResult;
import com.example.worker.messaging.outbound.DeadLetterPublisher;
import com.example.worker.messaging.outbound.WaitPublisher;
import com.example.worker.messaging.payload.NotificationMessagePayload;
import com.rabbitmq.client.Channel;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@ExtendWith(MockitoExtension.class)
class RabbitMQWorkConsumerTest {

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

	private RabbitMQWorkConsumer consumer;

	@BeforeEach
	void setUp() {
		when(meterRegistry.counter(any(), any(String[].class))).thenReturn(counter);
		consumer = new RabbitMQWorkConsumer(recordHandler, dlqPublisher, waitPublisher, meterRegistry,
			messageConverter);
	}

	@Test
	@DisplayName("payload 변환이 실패하면 DLQ로 보내고 ACK 한다")
	void onMessage_publishesToDlqWhenPayloadIsInvalid() throws IOException {
		Message message = message(1L, "msg-1");
		when(messageConverter.fromMessage(any())).thenReturn("not-a-payload");

		consumer.onMessage(message, channel);

		verify(dlqPublisher).publish("msg-1", null, null, "메시지 payload 변환 실패");
		verify(channel).basicAck(1L, false);
		verify(recordHandler, never()).process(any());
	}

	@Test
	@DisplayName("notificationId가 비어 있으면 DLQ로 보내고 ACK 한다")
	void onMessage_publishesToDlqWhenNotificationIdMissing() throws IOException {
		Message message = message(2L, "msg-2");
		NotificationMessagePayload payload = payloadOf(null);
		when(messageConverter.fromMessage(any())).thenReturn(payload);

		consumer.onMessage(message, channel);

		verify(dlqPublisher).publish("msg-2", payload, null, "payload 또는 notificationId 값이 비어 있습니다.");
		verify(channel).basicAck(2L, false);
	}

	@Test
	@DisplayName("성공 결과면 ACK 한다")
	void onMessage_acknowledgesWhenSuccessful() throws IOException {
		Message message = message(3L, "msg-3");
		when(messageConverter.fromMessage(any())).thenReturn(payloadOf(30L));
		when(recordHandler.process(any())).thenReturn(
			RecordProcessResult.success(3L, 30L, 0)
		);

		consumer.onMessage(message, channel);

		verify(channel).basicAck(3L, false);
		verify(dlqPublisher, never()).publish(any(), any(), any(), any());
		verify(waitPublisher, never()).publish(anyLong(), anyInt(), any(), any());
	}

	@Test
	@DisplayName("non-retryable 실패면 DLQ로 보내고 ACK 한다")
	void onMessage_publishesToDlqOnNonRetryableFailure() throws IOException {
		Message message = message(4L, "msg-4");
		NotificationMessagePayload payload = payloadOf(40L);
		when(messageConverter.fromMessage(any())).thenReturn(payload);
		when(recordHandler.process(any())).thenReturn(
			RecordProcessResult.nonRetryableFailure(4L, 40L, 0, "주소 오류")
		);

		consumer.onMessage(message, channel);

		verify(dlqPublisher).publish("msg-4", payload, 40L, "주소 오류");
		verify(channel).basicAck(4L, false);
	}

	@Test
	@DisplayName("retryable 실패면 Wait 큐로 보내고 ACK 한다")
	void onMessage_publishesToWaitOnRetryableFailure() throws IOException {
		Message message = message(5L, "msg-5");
		when(messageConverter.fromMessage(any())).thenReturn(payloadOf(50L));
		when(recordHandler.process(any())).thenReturn(
			RecordProcessResult.retryableFailure(5L, 50L, 1, "일시적 오류", 3000L)
		);

		consumer.onMessage(message, channel);

		verify(waitPublisher).publish(50L, 1, "일시적 오류", 3000L);
		verify(channel).basicAck(5L, false);
	}

	@Test
	@DisplayName("예기치 못한 예외가 발생하면 NACK 한다")
	void onMessage_nacksWhenUnexpectedExceptionThrown() throws IOException {
		Message message = message(6L, "msg-6");
		when(messageConverter.fromMessage(any())).thenReturn(payloadOf(60L));
		when(recordHandler.process(any())).thenThrow(new RuntimeException("unexpected"));

		consumer.onMessage(message, channel);

		verify(channel).basicNack(6L, false, false);
		verify(channel, never()).basicAck(anyLong(), anyBoolean());
	}

	private Message message(long deliveryTag, String messageId) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		properties.setMessageId(messageId);
		return new Message(new byte[0], properties);
	}

	private NotificationMessagePayload payloadOf(Long notificationId) {
		return new NotificationMessagePayload(notificationId, 0);
	}
}
