package com.example.infrastructure.repository;

import java.util.Optional;

import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.in.result.NotificationResult;
import com.example.application.port.out.cache.NotificationDetailCacheRepository;
import com.example.infrastructure.config.redis.NotificationCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationDetailCacheRepositoryImpl implements NotificationDetailCacheRepository {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final NotificationCacheProperties cacheProperties;

	@Override
	public Optional<NotificationResult> get(Long notificationId) {
		try {
			String value = redisTemplate.opsForValue().get(key(notificationId));
			if (value == null) {
				return Optional.empty();
			}
			return Optional.of(objectMapper.readValue(value, NotificationResult.class));
		} catch (RedisSystemException | JsonProcessingException e) {
			log.warn("notification detail cache 조회 실패: notificationId={}", notificationId, e);
			return Optional.empty();
		}
	}

	@Override
	public void put(Long notificationId, NotificationResult detail) {
		try {
			redisTemplate.opsForValue().set(
				key(notificationId),
				objectMapper.writeValueAsString(detail),
				cacheProperties.notificationDetailTtl()
			);
		} catch (RedisSystemException | JsonProcessingException e) {
			log.warn("notification detail cache 저장 실패: notificationId={}", notificationId, e);
		}
	}

	@Override
	public void evict(Long notificationId) {
		try {
			redisTemplate.delete(key(notificationId));
		} catch (RedisSystemException e) {
			log.warn("notification detail cache 삭제 실패: notificationId={}", notificationId, e);
		}
	}

	private String key(Long notificationId) {
		return "notification:detail:" + notificationId;
	}
}
