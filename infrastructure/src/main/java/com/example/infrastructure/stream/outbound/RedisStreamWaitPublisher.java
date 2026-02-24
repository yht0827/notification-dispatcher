package com.example.infrastructure.stream.outbound;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.config.StreamKeyType;
import com.example.infrastructure.stream.payload.NotificationWaitPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamWaitPublisher {

	private final StringRedisTemplate redisTemplate;
	private final NotificationStreamProperties properties;

	public void publish(Long notificationId, int retryCount, String lastError) {
		long nextRetryAt = System.currentTimeMillis() + properties.calculateRetryDelayMillis(retryCount);

		NotificationWaitPayload payload = NotificationWaitPayload.of(
			notificationId,
			retryCount,
			nextRetryAt,
			lastError
		);

		ObjectRecord<String, NotificationWaitPayload> record = StreamRecords
			.objectBacked(payload)
			.withStreamKey(properties.resolveKey(StreamKeyType.WAIT));

		RecordId recordId = redisTemplate.opsForStream().add(record);
		log.info("WAIT 스트림 발행: recordId={}, notificationId={}, retryCount={}, nextRetryAt={}",
			recordId, notificationId, retryCount, nextRetryAt);
	}
}
