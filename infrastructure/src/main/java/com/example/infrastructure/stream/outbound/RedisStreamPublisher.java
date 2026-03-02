package com.example.infrastructure.stream.outbound;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.application.port.out.NotificationEventPublisher;

import com.example.infrastructure.config.stream.NotificationStreamProperties;
import com.example.infrastructure.stream.StreamKeyType;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamPublisher implements NotificationEventPublisher {

	private final StringRedisTemplate redisTemplate;
	private final NotificationStreamProperties properties;

	@Override
	public void publish(Long notificationId) {
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(notificationId))
			.withStreamKey(properties.resolveKey(StreamKeyType.WORK));

		RecordId recordId = redisTemplate.opsForStream().add(record);
		log.info("Redis Stream 발행: recordId={}, notificationId={}", recordId, notificationId);
	}
}
