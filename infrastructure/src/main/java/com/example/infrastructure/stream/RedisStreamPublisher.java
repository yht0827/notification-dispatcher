package com.example.infrastructure.stream;

import java.util.Map;

import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.application.port.out.NotificationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamPublisher implements NotificationEventPublisher {

	private final StringRedisTemplate redisTemplate;
	private final NotificationStreamProperties properties;

	@Override
	public void publish(Long notificationId) {
		Map<String, String> message = Map.of(
			NotificationStreamMessage.NOTIFICATION_ID_FIELD,
			String.valueOf(notificationId)
		);
		RecordId recordId = redisTemplate.opsForStream().add(properties.key(), message);
		log.info("Redis Stream 발행: recordId={}, notificationId={}", recordId, notificationId);
	}
}
