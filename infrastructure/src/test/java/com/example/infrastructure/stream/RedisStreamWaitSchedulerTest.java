package com.example.infrastructure.stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyString;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.stream.NotificationStreamProperties;
import com.example.infrastructure.stream.inbound.RedisStreamWaitScheduler;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;
import com.example.infrastructure.stream.payload.NotificationWaitPayload;

@ExtendWith(MockitoExtension.class)
class RedisStreamWaitSchedulerTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private StreamOperations<String, Object, Object> streamOperations;

	private RedisStreamWaitScheduler scheduler;

	private static final String WORK_KEY = "notification-stream";
	private static final String WAIT_KEY = "notification-stream-wait";
	private static final String GROUP = "notification-group";

	@BeforeEach
	void setUp() {
		scheduler = new RedisStreamWaitScheduler(redisTemplate, streamProperties());

		when(redisTemplate.opsForStream()).thenReturn(streamOperations);
	}

	@Test
	@DisplayName("WAIT 스트림이 비어있으면 아무 작업도 하지 않는다")
	void processWaitingMessages_doesNothingWhenEmpty() {
		stubWaitRecords();

		scheduler.processWaitingMessages();

		verify(streamOperations, never()).add(ArgumentMatchers.<Record<String, ?>>any());
		verify(streamOperations, never()).delete(anyString(), any(RecordId.class));
	}

	@Test
	@DisplayName("nextRetryAt 시간이 지난 메시지는 WORK 스트림으로 재발행한다")
	void processWaitingMessages_republishesExpiredMessages() {
		AtomicReference<ObjectRecord<String, NotificationStreamPayload>> capturedRecord = capturePublishedWorkRecord();

		RecordId waitRecordId = RecordId.of("1-0");
		stubWaitRecords(waitRecord(waitRecordId, waitPayload(100L, 1, expiredTime(), "temporary error")));

		scheduler.processWaitingMessages();

		verify(streamOperations).add(ArgumentMatchers.<Record<String, ?>>any());

		ObjectRecord<String, NotificationStreamPayload> captured = capturedRecord.get();
		assertThat(captured.getStream()).isEqualTo(WORK_KEY);
		assertThat(captured.getValue().getNotificationId()).isEqualTo(100L);
		assertThat(captured.getValue().getRetryCount()).isEqualTo(2);

		verify(streamOperations).delete(WAIT_KEY, waitRecordId);
	}

	@Test
	@DisplayName("nextRetryAt 시간이 아직 안 된 메시지는 건너뛴다")
	void processWaitingMessages_skipsNotYetExpiredMessages() {
		RecordId waitRecordId = RecordId.of("1-0");
		stubWaitRecords(waitRecord(waitRecordId, waitPayload(100L, 1, futureTime(), "temporary error")));

		scheduler.processWaitingMessages();

		verify(streamOperations, never()).add(ArgumentMatchers.<Record<String, ?>>any());
		verify(streamOperations, never()).delete(anyString(), any(RecordId.class));
	}

	@Test
	@DisplayName("WAIT 스트림 조회 실패 시 예외를 전파하지 않는다")
	void processWaitingMessages_handlesQueryFailure() {
		when(streamOperations.range(
			eq(NotificationWaitPayload.class),
			eq(WAIT_KEY),
			ArgumentMatchers.<Range<String>>any(),
			any(Limit.class)
		)).thenThrow(new RuntimeException("Redis connection failed"));

		scheduler.processWaitingMessages();

		verify(streamOperations, never()).add(ArgumentMatchers.<Record<String, ?>>any());
	}

	@Test
	@DisplayName("재발행 실패 시 해당 메시지만 건너뛰고 계속 진행한다")
	void processWaitingMessages_continuesOnRepublishFailure() {
		long pastTime = expiredTime();

		ObjectRecord<String, NotificationWaitPayload> record1 = waitRecord(RecordId.of("1-0"),
			waitPayload(100L, 1, pastTime, "error1"));
		ObjectRecord<String, NotificationWaitPayload> record2 = waitRecord(RecordId.of("2-0"),
			waitPayload(200L, 0, pastTime, "error2"));
		stubWaitRecords(record1, record2);

		when(streamOperations.add(ArgumentMatchers.<Record<String, ?>>any()))
			.thenThrow(new RuntimeException("Publish failed"))
			.thenReturn(RecordId.of("3-0"));

		scheduler.processWaitingMessages();

		verify(streamOperations, times(2)).add(ArgumentMatchers.<Record<String, ?>>any());
		verify(streamOperations).delete(WAIT_KEY, RecordId.of("2-0"));
		verify(streamOperations, never()).delete(eq(WAIT_KEY), eq(RecordId.of("1-0")));
	}

	@Test
	@DisplayName("notificationId가 null인 페이로드는 건너뛴다")
	void processWaitingMessages_skipsNullNotificationIdPayload() {
		NotificationWaitPayload invalidPayload = new NotificationWaitPayload(null, 0, expiredTime(), "error");

		stubWaitRecords(waitRecord(RecordId.of("1-0"), invalidPayload));

		scheduler.processWaitingMessages();

		verify(streamOperations, never()).add(ArgumentMatchers.<Record<String, ?>>any());
		verify(streamOperations, never()).delete(anyString(), any(RecordId.class));
	}

	private NotificationStreamProperties streamProperties() {
		return new NotificationStreamProperties(
			WORK_KEY,
			GROUP,
			"consumer-1",
			1000, 10,
			"notification-stream-dlq",
			WAIT_KEY,
			3, 5000, 1000, 100
		);
	}

	private NotificationWaitPayload waitPayload(long notificationId, int retryCount, long nextRetryAt,
		String lastError) {
		return NotificationWaitPayload.of(notificationId, retryCount, nextRetryAt, lastError);
	}

	private ObjectRecord<String, NotificationWaitPayload> waitRecord(RecordId recordId,
		NotificationWaitPayload payload) {
		return StreamRecords
			.objectBacked(payload)
			.withStreamKey(WAIT_KEY)
			.withId(recordId);
	}

	private void stubWaitRecords(ObjectRecord<String, NotificationWaitPayload>... records) {
		when(streamOperations.range(
			eq(NotificationWaitPayload.class),
			eq(WAIT_KEY),
			ArgumentMatchers.any(),
			any(Limit.class)
		)).thenReturn(Arrays.asList(records));
	}

	private AtomicReference<ObjectRecord<String, NotificationStreamPayload>> capturePublishedWorkRecord() {
		AtomicReference<ObjectRecord<String, NotificationStreamPayload>> capturedRecord = new AtomicReference<>();
		when(streamOperations.add(ArgumentMatchers.<Record<String, ?>>any())).thenAnswer(invocation -> {
			ObjectRecord<String, NotificationStreamPayload> record = invocation.getArgument(0);
			capturedRecord.set(record);
			return RecordId.of("2-0");
		});
		return capturedRecord;
	}

	private long expiredTime() {
		return System.currentTimeMillis() - 1_000;
	}

	private long futureTime() {
		return System.currentTimeMillis() + 60_000;
	}
}
