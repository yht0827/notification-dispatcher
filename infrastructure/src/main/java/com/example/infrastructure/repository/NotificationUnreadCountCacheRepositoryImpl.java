package com.example.infrastructure.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.infrastructure.config.redis.NotificationCacheProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationUnreadCountCacheRepositoryImpl implements NotificationUnreadCountCacheRepository {

	private final StringRedisTemplate redisTemplate;
	private final NotificationCacheProperties cacheProperties;

	@Override
	public boolean enabled() {
		return cacheProperties.unreadCountEnabled();
	}

	@Override
	public Optional<Long> get(String clientId, String receiver) {
		try {
			String value = redisTemplate.opsForValue().get(key(clientId, receiver));
			if (value == null) {
				return Optional.empty();
			}
			return Optional.of(Long.parseLong(value));
		} catch (RedisSystemException | NumberFormatException e) {
			log.warn("unread count cache 조회 실패: clientId={}, receiver={}", clientId, receiver, e);
			return Optional.empty();
		}
	}

	@Override
	public void put(String clientId, String receiver, long unreadCount) {
		try {
			redisTemplate.opsForValue().set(
				key(clientId, receiver),
				Long.toString(unreadCount),
				cacheProperties.unreadCountTtl()
			);
		} catch (RedisSystemException e) {
			log.warn("unread count cache 저장 실패: clientId={}, receiver={}", clientId, receiver, e);
		}
	}

	@Override
	public void evict(String clientId, String receiver) {
		try {
			redisTemplate.delete(key(clientId, receiver));
		} catch (RedisSystemException e) {
			log.warn("unread count cache 삭제 실패: clientId={}, receiver={}", clientId, receiver, e);
		}
	}

	private String key(String clientId, String receiver) {
		return "notification:unread-count:" + clientId + ":" + encode(receiver);
	}

	private String encode(String receiver) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(receiver.getBytes(StandardCharsets.UTF_8));
	}
}
