package com.example.infrastructure.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.stream.inbound.RedisStreamInitializer;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

@ExtendWith(MockitoExtension.class)
class RedisStreamInitializerTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private StreamOperations<String, Object, Object> streamOperations;

	@Mock
	private RedisStreamWaitPublisher waitPublisher;

	@Mock
	private PendingMessages emptyPendingMessages;

	private RedisStreamInitializer initializer;

	@BeforeEach
	void setUp() {
		NotificationStreamProperties properties = new NotificationStreamProperties(
			"notification-stream",
			"notification-group",
			"consumer-1",
			1000, 10,
			"notification-stream-dlq",
			"notification-stream-wait",
			3, 5000, 1000
		);
		initializer = new RedisStreamInitializer(redisTemplate, waitPublisher, properties);
		lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
		lenient().when(emptyPendingMessages.isEmpty()).thenReturn(true);
	}

	@Test
	@DisplayName("Consumer Group이 없으면 새로 생성한다")
	void init_createsConsumerGroupWhenNotExists() {
		// given
		when(streamOperations.createGroup("notification-stream", "notification-group")).thenReturn("OK");
		when(streamOperations.pending("notification-stream", "notification-group", Range.closed("-", "+"), 100))
			.thenReturn(emptyPendingMessages);

		// when
		initializer.init();

		// then
		verify(streamOperations).createGroup("notification-stream", "notification-group");
	}

	@Test
	@DisplayName("Consumer Group이 이미 존재하면 BUSYGROUP 오류를 무시한다")
	void init_ignoresBusyGroupError() {
		// given
		when(streamOperations.createGroup("notification-stream", "notification-group"))
			.thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));
		when(streamOperations.pending("notification-stream", "notification-group", Range.closed("-", "+"), 100))
			.thenReturn(emptyPendingMessages);

		// when
		initializer.init();

		// then - 예외 없이 정상 종료
		verify(streamOperations).createGroup("notification-stream", "notification-group");
	}

	@Test
	@DisplayName("Pending 메시지가 없으면 복구를 건너뛴다")
	void init_skipsRecoveryWhenNoPendingMessages() {
		// given
		when(streamOperations.createGroup(any(), any())).thenReturn("OK");
		when(streamOperations.pending("notification-stream", "notification-group", Range.closed("-", "+"), 100))
			.thenReturn(emptyPendingMessages);

		// when
		initializer.init();

		// then
		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
	}

	@Test
	@DisplayName("Pending 메시지가 있으면 WAIT 스트림으로 복구한다")
	@SuppressWarnings("unchecked")
	void init_recoversPendingMessagesToWaitStream() {
		// given
		when(streamOperations.createGroup(any(), any())).thenReturn("OK");

		RecordId pendingRecordId = RecordId.of("1-0");
		PendingMessage pendingMessage = mock(PendingMessage.class);
		when(pendingMessage.getId()).thenReturn(pendingRecordId);

		PendingMessages pendingMessages = mock(PendingMessages.class);
		when(pendingMessages.isEmpty()).thenReturn(false);
		when(pendingMessages.iterator()).thenReturn(List.of(pendingMessage).iterator());

		when(streamOperations.pending("notification-stream", "notification-group", Range.closed("-", "+"), 100))
			.thenReturn(pendingMessages);

		NotificationStreamPayload payload = new NotificationStreamPayload(100L, 1);
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(payload)
			.withStreamKey("notification-stream")
			.withId(pendingRecordId);

		when(streamOperations.range(
			eq(NotificationStreamPayload.class),
			eq("notification-stream"),
			eq(Range.closed("1-0", "1-0"))
		)).thenReturn(List.of(record));

		// when
		initializer.init();

		// then
		verify(waitPublisher).publish(100L, 1, "시작 시 Pending 복구");
		verify(streamOperations).acknowledge("notification-stream", "notification-group", pendingRecordId);
	}

	@Test
	@DisplayName("Pending 메시지 조회 실패 시 예외를 전파하지 않는다")
	void init_doesNotPropagateExceptionWhenPendingQueryFails() {
		// given
		when(streamOperations.createGroup(any(), any())).thenReturn("OK");
		when(streamOperations.pending("notification-stream", "notification-group", Range.closed("-", "+"), 100))
			.thenThrow(new RuntimeException("Redis connection failed"));

		// when
		initializer.init();

		// then - 예외 없이 정상 종료
		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
	}

	@Test
	@DisplayName("개별 Pending 메시지 복구 실패 시 다른 메시지 복구를 계속한다")
	@SuppressWarnings("unchecked")
	void init_continuesRecoveryWhenSingleMessageFails() {
		// given
		when(streamOperations.createGroup(any(), any())).thenReturn("OK");

		RecordId recordId1 = RecordId.of("1-0");
		RecordId recordId2 = RecordId.of("2-0");

		PendingMessage pm1 = mock(PendingMessage.class);
		PendingMessage pm2 = mock(PendingMessage.class);
		when(pm1.getId()).thenReturn(recordId1);
		when(pm2.getId()).thenReturn(recordId2);

		PendingMessages pendingMessages = mock(PendingMessages.class);
		when(pendingMessages.isEmpty()).thenReturn(false);
		when(pendingMessages.iterator()).thenReturn(List.of(pm1, pm2).iterator());

		when(streamOperations.pending("notification-stream", "notification-group", Range.closed("-", "+"), 100))
			.thenReturn(pendingMessages);

		// 첫 번째 메시지 조회 실패
		when(streamOperations.range(
			eq(NotificationStreamPayload.class),
			eq("notification-stream"),
			eq(Range.closed("1-0", "1-0"))
		)).thenReturn(Collections.emptyList());

		// 두 번째 메시지 조회 성공
		NotificationStreamPayload payload2 = new NotificationStreamPayload(200L, 0);
		ObjectRecord<String, NotificationStreamPayload> record2 = StreamRecords
			.objectBacked(payload2)
			.withStreamKey("notification-stream")
			.withId(recordId2);

		when(streamOperations.range(
			eq(NotificationStreamPayload.class),
			eq("notification-stream"),
			eq(Range.closed("2-0", "2-0"))
		)).thenReturn(List.of(record2));

		// when
		initializer.init();

		// then - 두 번째 메시지만 복구됨
		verify(waitPublisher).publish(200L, 0, "시작 시 Pending 복구");
		verify(streamOperations).acknowledge("notification-stream", "notification-group", recordId2);
	}
}
