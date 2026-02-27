package com.example.infrastructure.stream.outbound;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.config.StreamKeyType;
import com.example.infrastructure.stream.exception.DeadLetterPublishException;
import com.example.infrastructure.stream.payload.NotificationDeadLetterPayload;
import com.example.infrastructure.stream.port.DeadLetterPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamDlqPublisher implements DeadLetterPublisher {

	private final StringRedisTemplate redisTemplate;
	private final NotificationStreamProperties properties;

	@Override
	public void publish(RecordId sourceRecordId, Object payload, Long notificationId, String reason) {
		try {
			ObjectRecord<String, NotificationDeadLetterPayload> dlqRecord = StreamRecords
				.objectBacked(NotificationDeadLetterPayload.from(sourceRecordId, payload, notificationId, reason))
				.withStreamKey(properties.resolveKey(StreamKeyType.DEAD_LETTER));

			RecordId dlqRecordId = redisTemplate.opsForStream().add(dlqRecord);
			log.warn("DLQ 전송 완료: dlqRecordId={}, sourceRecordId={}, notificationId={}", dlqRecordId, sourceRecordId,
				notificationId);
		} catch (Exception e) {
			log.error("DLQ 전송 실패: sourceRecordId={}, error={}", sourceRecordId, e.getMessage(), e);
			throw new DeadLetterPublishException("DLQ 전송 실패", e);
		}
	}
}
