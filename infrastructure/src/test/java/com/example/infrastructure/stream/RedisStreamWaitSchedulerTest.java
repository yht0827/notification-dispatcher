package com.example.infrastructure.stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.stream.config.NotificationStreamProperties;
import com.example.infrastructure.stream.inbound.RedisStreamWaitScheduler;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

@ExtendWith(MockitoExtension.class)
class RedisStreamWaitSchedulerTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private StreamOperations<String, Object, Object> streamOperations;

	private RedisStreamWaitScheduler scheduler;
	private NotificationStreamProperties properties;

	@BeforeEach
	void setUp() {
		properties = new NotificationStreamProperties(
			"notification-stream",
			"notification-group",
			"consumer-1",
			1000, 10,
			"notification-stream-dlq",
			"notification-stream-wait",
			3, 5000, 1000
		);
		scheduler = new RedisStreamWaitScheduler(redisTemplate, properties);
		lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);
	}

	@Test
	@DisplayName("WAIT 스트림이 비어있으면 아무 작업도 하지 않는다")
	void processWaitingMessages_doesNothingWhenEmpty() {
		// given
		when(streamOperations.range(
			eq("notification-stream-wait"),
			any(Range.class),
			any(Limit.class)
		)).thenReturn(Collections.emptyList());

		// when
		scheduler.processWaitingMessages();

		// then
		verify(streamOperations, never()).add(any(ObjectRecord.class));
		verify(streamOperations, never()).delete(anyString(), any(RecordId.class));
	}

	@Test
	@DisplayName("nextRetryAt 시간이 지난 메시지는 WORK 스트림으로 재발행한다")
	@SuppressWarnings("unchecked")
	void processWaitingMessages_republishesExpiredMessages() {
		// given
		Map<Object, Object> waitPayload = new HashMap<>();
		waitPayload.put("notificationId", "100");
		waitPayload.put("retryCount", "1");
		waitPayload.put("nextRetryAt", String.valueOf(System.currentTimeMillis() - 1000)); // 과거 시간
		waitPayload.put("lastError", "temporary error");

		RecordId waitRecordId = RecordId.of("1-0");
		MapRecord<String, Object, Object> waitRecord = StreamRecords
			.mapBacked(waitPayload)
			.withStreamKey("notification-stream-wait")
			.withId(waitRecordId);

		when(streamOperations.range(
			eq("notification-stream-wait"),
			any(Range.class),
			any(Limit.class)
		)).thenReturn(List.of(waitRecord));

		when(streamOperations.add(any(ObjectRecord.class))).thenReturn(RecordId.of("2-0"));

		// when
		scheduler.processWaitingMessages();

		// then
		ArgumentCaptor<ObjectRecord<String, NotificationStreamPayload>> captor =
			ArgumentCaptor.forClass(ObjectRecord.class);
		verify(streamOperations).add(captor.capture());

		ObjectRecord<String, NotificationStreamPayload> captured = captor.getValue();
		assert captured.getStream().equals("notification-stream");
		assert captured.getValue().notificationIdAsLong() == 100L;
		assert captured.getValue().getRetryCount() == 2; // retryCount + 1

		verify(streamOperations).delete("notification-stream-wait", waitRecordId);
	}

	@Test
	@DisplayName("nextRetryAt 시간이 아직 안 된 메시지는 건너뛴다")
	void processWaitingMessages_skipsNotYetExpiredMessages() {
		// given
		Map<Object, Object> waitPayload = new HashMap<>();
		waitPayload.put("notificationId", "100");
		waitPayload.put("retryCount", "1");
		waitPayload.put("nextRetryAt", String.valueOf(System.currentTimeMillis() + 60000)); // 미래 시간
		waitPayload.put("lastError", "temporary error");

		RecordId waitRecordId = RecordId.of("1-0");
		MapRecord<String, Object, Object> waitRecord = StreamRecords
			.mapBacked(waitPayload)
			.withStreamKey("notification-stream-wait")
			.withId(waitRecordId);

		when(streamOperations.range(
			eq("notification-stream-wait"),
			any(Range.class),
			any(Limit.class)
		)).thenReturn(List.of(waitRecord));

		// when
		scheduler.processWaitingMessages();

		// then
		verify(streamOperations, never()).add(any(ObjectRecord.class));
		verify(streamOperations, never()).delete(anyString(), any(RecordId.class));
	}

	@Test
	@DisplayName("WAIT 스트림 조회 실패 시 예외를 전파하지 않는다")
	void processWaitingMessages_handlesQueryFailure() {
		// given
		when(streamOperations.range(
			eq("notification-stream-wait"),
			any(Range.class),
			any(Limit.class)
		)).thenThrow(new RuntimeException("Redis connection failed"));

		// when
		scheduler.processWaitingMessages();

		// then - 예외 없이 정상 종료
		verify(streamOperations, never()).add(any(ObjectRecord.class));
	}

	@Test
	@DisplayName("재발행 실패 시 해당 메시지만 건너뛰고 계속 진행한다")
	@SuppressWarnings("unchecked")
	void processWaitingMessages_continuesOnRepublishFailure() {
		// given
		long pastTime = System.currentTimeMillis() - 1000;

		Map<Object, Object> payload1 = new HashMap<>();
		payload1.put("notificationId", "100");
		payload1.put("retryCount", "1");
		payload1.put("nextRetryAt", String.valueOf(pastTime));
		payload1.put("lastError", "error1");

		Map<Object, Object> payload2 = new HashMap<>();
		payload2.put("notificationId", "200");
		payload2.put("retryCount", "0");
		payload2.put("nextRetryAt", String.valueOf(pastTime));
		payload2.put("lastError", "error2");

		MapRecord<String, Object, Object> record1 = StreamRecords
			.mapBacked(payload1)
			.withStreamKey("notification-stream-wait")
			.withId(RecordId.of("1-0"));

		MapRecord<String, Object, Object> record2 = StreamRecords
			.mapBacked(payload2)
			.withStreamKey("notification-stream-wait")
			.withId(RecordId.of("2-0"));

		when(streamOperations.range(
			eq("notification-stream-wait"),
			any(Range.class),
			any(Limit.class)
		)).thenReturn(List.of(record1, record2));

		// 첫 번째 재발행 실패
		when(streamOperations.add(any(ObjectRecord.class)))
			.thenThrow(new RuntimeException("Publish failed"))
			.thenReturn(RecordId.of("3-0"));

		// when
		scheduler.processWaitingMessages();

		// then - 두 번째 메시지는 성공
		verify(streamOperations, times(2)).add(any(ObjectRecord.class));
		verify(streamOperations).delete("notification-stream-wait", RecordId.of("2-0"));
		verify(streamOperations, never()).delete(eq("notification-stream-wait"), eq(RecordId.of("1-0")));
	}

	@Test
	@DisplayName("잘못된 페이로드 형식은 건너뛴다")
	void processWaitingMessages_skipsInvalidPayload() {
		// given
		Map<Object, Object> invalidPayload = new HashMap<>();
		invalidPayload.put("notificationId", "invalid-id");
		invalidPayload.put("retryCount", "not-a-number");

		MapRecord<String, Object, Object> record = StreamRecords
			.mapBacked(invalidPayload)
			.withStreamKey("notification-stream-wait")
			.withId(RecordId.of("1-0"));

		when(streamOperations.range(
			eq("notification-stream-wait"),
			any(Range.class),
			any(Limit.class)
		)).thenReturn(List.of(record));

		// when
		scheduler.processWaitingMessages();

		// then - 예외 없이 정상 종료, 재발행 없음
		verify(streamOperations, never()).add(any(ObjectRecord.class));
		verify(streamOperations, never()).delete(anyString(), any(RecordId.class));
	}
}
