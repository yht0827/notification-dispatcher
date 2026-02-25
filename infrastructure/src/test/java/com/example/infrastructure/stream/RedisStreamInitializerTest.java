package com.example.infrastructure.stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
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

	private RedisStreamInitializer initializer;

	private static final String STREAM_KEY = "notification-stream";
	private static final String GROUP = "notification-group";
	private static final Range<String> PENDING_RANGE = Range.closed("-", "+");
	private static final int PENDING_FETCH_SIZE = 100;
	private static final String RECOVERY_REASON = "시작 시 Pending 복구";

	@BeforeEach
	void setUp() {
		initializer = new RedisStreamInitializer(redisTemplate, waitPublisher, streamProperties());

		when(redisTemplate.opsForStream()).thenReturn(streamOperations);
	}

	@Test
	@DisplayName("Consumer Group이 없으면 새로 생성한다")
	void init_createsConsumerGroupWhenNotExists() {
		stubCreateGroupOk();
		stubPendingMessages();

		initializer.init();

		verify(streamOperations).createGroup(STREAM_KEY, GROUP);
	}

	@Test
	@DisplayName("Consumer Group이 이미 존재하면 BUSYGROUP 오류를 무시한다")
	void init_ignoresBusyGroupError() {
		stubPendingMessages();

		when(streamOperations.createGroup(STREAM_KEY, GROUP))
			.thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

		initializer.init();

		verify(streamOperations).createGroup(STREAM_KEY, GROUP);
	}

	@Test
	@DisplayName("Pending 메시지가 없으면 복구를 건너뛴다")
	void init_skipsRecoveryWhenNoPendingMessages() {
		stubCreateGroupOk();
		stubPendingMessages();

		initializer.init();

		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
	}

	@Test
	@DisplayName("Pending 메시지가 있으면 WAIT 스트림으로 복구한다")
	void init_recoversPendingMessagesToWaitStream() {
		stubCreateGroupOk();

		RecordId pendingRecordId = RecordId.of("1-0");
		stubPendingMessages(pendingRecordId);
		stubRangeRecord(pendingRecordId, 100L, 1);

		initializer.init();

		verify(waitPublisher).publish(100L, 1, RECOVERY_REASON);
		verify(streamOperations).acknowledge(STREAM_KEY, GROUP, pendingRecordId);
	}

	@Test
	@DisplayName("Pending 메시지 조회 실패 시 예외를 전파하지 않는다")
	void init_doesNotPropagateExceptionWhenPendingQueryFails() {
		stubCreateGroupOk();

		when(streamOperations.pending(STREAM_KEY, GROUP, PENDING_RANGE, PENDING_FETCH_SIZE))
			.thenThrow(new RuntimeException("Redis connection failed"));

		initializer.init();

		verify(waitPublisher, never()).publish(anyLong(), anyInt(), anyString());
	}

	@Test
	@DisplayName("개별 Pending 메시지 복구 실패 시 다른 메시지 복구를 계속한다")
	void init_continuesRecoveryWhenSingleMessageFails() {
		stubCreateGroupOk();

		RecordId recordId1 = RecordId.of("1-0");
		RecordId recordId2 = RecordId.of("2-0");
		stubPendingMessages(recordId1, recordId2);

		stubRangeEmpty(recordId1);
		stubRangeRecord(recordId2, 200L, 0);

		initializer.init();

		verify(waitPublisher).publish(200L, 0, RECOVERY_REASON);
		verify(streamOperations).acknowledge(STREAM_KEY, GROUP, recordId2);
	}

	private NotificationStreamProperties streamProperties() {
		return new NotificationStreamProperties(
			STREAM_KEY,
			GROUP,
			"consumer-1",
			1000, 10,
			"notification-stream-dlq",
			"notification-stream-wait",
			3, 5000, 1000, 100
		);
	}

	private void stubCreateGroupOk() {
		when(streamOperations.createGroup(anyString(), anyString())).thenReturn("OK");
	}

	private void stubPendingMessages(RecordId... recordIds) {
		PendingMessages pendingMessages = mock(PendingMessages.class);
		List<PendingMessage> pendingMessageList = new ArrayList<>();

		for (RecordId recordId : recordIds) {
			pendingMessageList.add(pendingMessage(recordId));
		}

		when(pendingMessages.isEmpty()).thenReturn(recordIds.length == 0);
		if (recordIds.length > 0) {
			when(pendingMessages.iterator()).thenReturn(pendingMessageList.iterator());
		}
		when(streamOperations.pending(STREAM_KEY, GROUP, PENDING_RANGE, PENDING_FETCH_SIZE)).thenReturn(pendingMessages);
	}

	private PendingMessage pendingMessage(RecordId recordId) {
		PendingMessage pendingMessage = mock(PendingMessage.class);
		when(pendingMessage.getId()).thenReturn(recordId);
		return pendingMessage;
	}

	private void stubRangeRecord(RecordId recordId, long notificationId, int retryCount) {
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(notificationId, retryCount))
			.withStreamKey(STREAM_KEY)
			.withId(recordId);

		when(streamOperations.range(
			eq(NotificationStreamPayload.class),
			eq(STREAM_KEY),
			eq(singleRecordRange(recordId))
		)).thenReturn(List.of(record));
	}

	private void stubRangeEmpty(RecordId recordId) {
		when(streamOperations.range(
			eq(NotificationStreamPayload.class),
			eq(STREAM_KEY),
			eq(singleRecordRange(recordId))
		)).thenReturn(List.of());
	}

	private Range<String> singleRecordRange(RecordId recordId) {
		String value = recordId.getValue();
		return Range.closed(value, value);
	}
}
