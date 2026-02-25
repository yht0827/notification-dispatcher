package com.example.infrastructure.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.stream.inbound.RedisStreamConsumer;
import com.example.infrastructure.stream.inbound.RedisStreamRecordHandler;
import com.example.infrastructure.stream.exception.DeadLetterPublishException;
import com.example.infrastructure.stream.exception.NonRetryableStreamMessageException;
import com.example.infrastructure.stream.exception.RetryableStreamMessageException;
import com.example.infrastructure.stream.outbound.RedisStreamDlqPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

@ExtendWith(MockitoExtension.class)
class RedisStreamConsumerTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private StreamOperations<String, Object, Object> streamOperations;

	@Mock
	private RedisStreamRecordHandler recordHandler;

	@Mock
	private RedisStreamDlqPublisher dlqPublisher;

	@Mock
	private RedisStreamWaitPublisher waitPublisher;

	private RedisStreamConsumer consumer;

	@BeforeEach
	void setUp() {
		NotificationStreamProperties properties = new NotificationStreamProperties(
			"notification-stream",
			"notification-group",
			"consumer-1",
			1000,
			10,
			"notification-stream-dlq",
			"notification-stream-wait",
			3,
			5000,
			1000,
			100
		);
		consumer = new RedisStreamConsumer(redisTemplate, recordHandler, dlqPublisher, waitPublisher, properties);
		lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
	}

	@Test
	@DisplayName("처리 중 non-retryable 오류는 DLQ 전송 후 ACK 한다")
	void onMessage_sendsDlqAndAckWhenProcessFailsNonRetryable() {
		// given
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(10L, 0))
			.withStreamKey("notification-stream")
			.withId(RecordId.of("2-0"));

		doThrow(new NonRetryableStreamMessageException("max retry exceeded"))
			.when(recordHandler)
			.process(10L, 0);

		// when
		consumer.onMessage(record);

		// then
		verify(dlqPublisher).publish(RecordId.of("2-0"), record.getValue(), 10L, "max retry exceeded");
		verify(streamOperations).acknowledge("notification-stream", "notification-group", RecordId.of("2-0"));
	}

	@Test
	@DisplayName("notificationId가 null이면 DLQ 전송 후 ACK 한다")
	void onMessage_sendsDlqAndAckWhenNotificationIdNull() {
		NotificationStreamPayload payload = new NotificationStreamPayload();
		payload.setNotificationId(null);
		payload.setRetryCount(0);

		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(payload)
			.withStreamKey("notification-stream")
			.withId(RecordId.of("2-1"));

		consumer.onMessage(record);

		verify(dlqPublisher).publish(
			eq(RecordId.of("2-1")),
			eq(payload),
			isNull(),
			contains("notificationId 값이 비어 있습니다")
		);
		verify(streamOperations).acknowledge("notification-stream", "notification-group", RecordId.of("2-1"));
	}

	@Test
	@DisplayName("처리 성공 시 ACK만 수행한다")
	void onMessage_acknowledgesWhenProcessSucceeds() {
		// given
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(20L, 0))
			.withStreamKey("notification-stream")
			.withId(RecordId.of("3-0"));

		// when
		consumer.onMessage(record);

		// then
		verify(recordHandler).process(20L, 0);
		verify(streamOperations).acknowledge("notification-stream", "notification-group", RecordId.of("3-0"));
	}

	@Test
	@DisplayName("처리 중 retryable 오류는 WAIT 스트림으로 전송 후 ACK 한다")
	void onMessage_sendsToWaitStreamWhenProcessFailsRetryable() {
		// given
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(30L, 1))
			.withStreamKey("notification-stream")
			.withId(RecordId.of("4-0"));

		doThrow(new RetryableStreamMessageException("일시적 오류"))
			.when(recordHandler)
			.process(30L, 1);

		// when
		consumer.onMessage(record);

		// then
		verify(waitPublisher).publish(30L, 1, "일시적 오류");
		verify(streamOperations).acknowledge("notification-stream", "notification-group", RecordId.of("4-0"));
	}

	@Test
	@DisplayName("DLQ 전송 실패 시 ACK 하지 않고 예외를 전파한다")
	void onMessage_doesNotAckWhenDlqPublishFails() {
		// given
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(40L, 0))
			.withStreamKey("notification-stream")
			.withId(RecordId.of("5-0"));

		doThrow(new NonRetryableStreamMessageException("max retry exceeded"))
			.when(recordHandler)
			.process(40L, 0);
		doThrow(new DeadLetterPublishException("dlq publish failed"))
			.when(dlqPublisher)
			.publish(RecordId.of("5-0"), record.getValue(), 40L, "max retry exceeded");

		assertThatThrownBy(() -> consumer.onMessage(record))
			.isInstanceOf(DeadLetterPublishException.class)
			.hasMessageContaining("dlq publish failed");

		verify(streamOperations, never()).acknowledge("notification-stream", "notification-group", RecordId.of("5-0"));
	}

	@Test
	@DisplayName("WAIT 전송 실패 시 ACK 하지 않고 예외를 전파한다")
	void onMessage_doesNotAckWhenWaitPublishFails() {
		// given
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(50L, 1))
			.withStreamKey("notification-stream")
			.withId(RecordId.of("6-0"));

		doThrow(new RetryableStreamMessageException("temporary failure"))
			.when(recordHandler)
			.process(50L, 1);
		doThrow(new IllegalStateException("wait publish failed"))
			.when(waitPublisher)
			.publish(50L, 1, "temporary failure");

		assertThatThrownBy(() -> consumer.onMessage(record))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("wait publish failed");

		verify(streamOperations, never()).acknowledge("notification-stream", "notification-group", RecordId.of("6-0"));
	}

	@Test
	@DisplayName("예상치 못한 예외는 ACK 없이 그대로 전파한다")
	void onMessage_propagatesUnexpectedExceptionWithoutAck() {
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(60L, 1))
			.withStreamKey("notification-stream")
			.withId(RecordId.of("7-0"));

		doThrow(new IllegalStateException("unexpected failure"))
			.when(recordHandler)
			.process(60L, 1);

		assertThatThrownBy(() -> consumer.onMessage(record))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unexpected failure");

		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
		verify(dlqPublisher, never()).publish(any(), any(), any(), anyString());
		verify(streamOperations, never()).acknowledge("notification-stream", "notification-group", RecordId.of("7-0"));
	}
}
