package com.example.infrastructure.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.infrastructure.config.redis.NotificationCacheProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationUnreadCountCacheRepositoryImpl implements NotificationUnreadCountCacheRepository {

	private static final String INCR_IF_EXISTS_SCRIPT =
		"if redis.call('EXISTS', KEYS[1]) == 1 then return redis.call('INCR', KEYS[1]) else return -1 end";

	private static final String DECR_IF_EXISTS_SCRIPT =
		"if redis.call('EXISTS', KEYS[1]) == 1 then " +
		"  local v = tonumber(redis.call('GET', KEYS[1])) " +
		"  if v and v > 0 then return redis.call('DECR', KEYS[1]) else return 0 end " +
		"else return -1 end";

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

	@Override
	public void increment(String clientId, String receiver) {
		String key = key(clientId, receiver);
		try {
			DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_IF_EXISTS_SCRIPT, Long.class);
			Long result = redisTemplate.execute(script, List.of(key));
			if (result == null || result == -1L) {
				log.debug("unread count cache increment 생략 (키 없음): clientId={}, receiver={}", clientId, receiver);
			} else {
				log.debug("unread count cache increment 완료: clientId={}, receiver={}, newValue={}", clientId, receiver,
					result);
			}
		} catch (RedisSystemException e) {
			log.warn("unread count cache increment 실패: clientId={}, receiver={}", clientId, receiver, e);
		}
	}

	@Override
	public void decrement(String clientId, String receiver) {
		String key = key(clientId, receiver);
		try {
			DefaultRedisScript<Long> script = new DefaultRedisScript<>(DECR_IF_EXISTS_SCRIPT, Long.class);
			Long result = redisTemplate.execute(script, List.of(key));
			if (result == null || result == -1L) {
				log.debug("unread count cache decrement 생략 (키 없음): clientId={}, receiver={}", clientId, receiver);
			} else {
				log.debug("unread count cache decrement 완료: clientId={}, receiver={}, newValue={}", clientId, receiver,
					result);
			}
		} catch (RedisSystemException e) {
			log.warn("unread count cache decrement 실패: clientId={}, receiver={}", clientId, receiver, e);
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
