package com.example.infrastructure.stream.outbound;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.config.StreamKeyType;
import com.example.infrastructure.stream.payload.NotificationWaitPayload;
import com.example.infrastructure.stream.port.WaitPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamWaitPublisher implements WaitPublisher {

	private final StringRedisTemplate redisTemplate;
	private final NotificationStreamProperties properties;

	@Override
	public void publish(Long notificationId, int retryCount, String lastError) {
		// 재시도 정책에 따라 다음 재처리 시각(epoch millis)을 계산
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

		// WaitScheduler가 nextRetryAt 이후 WORK로 재발행
		RecordId recordId = redisTemplate.opsForStream().add(record);
		log.info("WAIT 스트림 발행: recordId={}, notificationId={}, retryCount={}, nextRetryAt={}",
			recordId, notificationId, retryCount, nextRetryAt);
	}
}
