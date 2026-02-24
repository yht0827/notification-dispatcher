package com.example.infrastructure.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.stream.outbound.RedisStreamDlqPublisher;
import com.example.infrastructure.stream.payload.NotificationDeadLetterPayload;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

@ExtendWith(MockitoExtension.class)
class RedisStreamDlqPublisherTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private StreamOperations<String, Object, Object> streamOperations;

	@Test
	@DisplayName("deadLetterKey가 있으면 해당 키로 DLQ를 발행한다")
	void publish_usesConfiguredDeadLetterKey() {
		NotificationStreamProperties properties = new NotificationStreamProperties(
			"notification-stream",
			"notification-group",
			"consumer-1",
			1000,
			10,
			"custom-dlq",
			"notification-stream-wait",
			3,
			5000,
			1000
		);
		RedisStreamDlqPublisher publisher = new RedisStreamDlqPublisher(redisTemplate, properties);

		when(redisTemplate.opsForStream()).thenReturn(streamOperations);
		when(streamOperations.add(any(ObjectRecord.class))).thenReturn(RecordId.of("1-0"));

		publisher.publish(RecordId.of("10-0"), new NotificationStreamPayload(10L), 10L, "non-retryable");

		ArgumentCaptor<ObjectRecord> recordCaptor = ArgumentCaptor.forClass(ObjectRecord.class);
		verify(streamOperations).add(recordCaptor.capture());

		ObjectRecord<?, ?> record = recordCaptor.getValue();
		assertThat(record.getStream()).isEqualTo("custom-dlq");
		assertThat(record.getValue()).isInstanceOf(NotificationDeadLetterPayload.class);
		NotificationDeadLetterPayload message = (NotificationDeadLetterPayload)record.getValue();
		assertThat(message.recordId()).isEqualTo("10-0");
		assertThat(message.notificationId()).isEqualTo("10");
		assertThat(message.reason()).isEqualTo("non-retryable");
		assertThat(message.payload()).isNotBlank();
		assertThat(message.failedAt()).isNotBlank();
	}

	@Test
	@DisplayName("deadLetterKey가 비어 있으면 기본 suffix 키를 사용한다")
	void publish_usesDefaultSuffixWhenDeadLetterKeyBlank() {
		NotificationStreamProperties properties = new NotificationStreamProperties(
			"main-stream",
			"notification-group",
			"consumer-1",
			1000,
			10,
			"  ",
			"main-stream-wait",
			3,
			5000,
			1000
		);
		RedisStreamDlqPublisher publisher = new RedisStreamDlqPublisher(redisTemplate, properties);

		when(redisTemplate.opsForStream()).thenReturn(streamOperations);
		when(streamOperations.add(any(ObjectRecord.class))).thenReturn(RecordId.of("2-0"));

		publisher.publish(RecordId.of("20-0"), "payload", 20L, "reason");

		ArgumentCaptor<ObjectRecord> recordCaptor = ArgumentCaptor.forClass(ObjectRecord.class);
		verify(streamOperations).add(recordCaptor.capture());
		assertThat(recordCaptor.getValue().getStream()).isEqualTo("main-stream-dlq");
	}
}
