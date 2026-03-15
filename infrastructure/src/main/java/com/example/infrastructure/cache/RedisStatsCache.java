package com.example.infrastructure.cache;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.example.application.port.in.result.NotificationStatsResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisStatsCache {

	private static final String KEY_ALL = "admin:stats:all";
	private static final String KEY_CLIENT_PREFIX = "admin:stats:client:";

	private final RedisTemplate<String, Object> objectRedisTemplate;
	private final CacheProperties cacheProperties;

	public NotificationStatsResult get(String key, Supplier<NotificationStatsResult> loader) {
		String redisKey = toRedisKey(key);
		NotificationStatsResult cached = (NotificationStatsResult)objectRedisTemplate.opsForValue().get(redisKey);
		if (cached != null) {
			return cached;
		}
		NotificationStatsResult result = loader.get();
		objectRedisTemplate.opsForValue().set(redisKey, result, cacheProperties.resolveTtlSeconds(), TimeUnit.SECONDS);
		return result;
	}

	public void evict(String key) {
		objectRedisTemplate.delete(toRedisKey(key));
	}

	public void evictAll() {
		Set<String> keys = objectRedisTemplate.keys("admin:stats:*");
		if (!keys.isEmpty()) {
			objectRedisTemplate.delete(keys);
		}
	}

	private String toRedisKey(String key) {
		if (key.equals(KEY_ALL)) {
			return KEY_ALL;
		}
		return KEY_CLIENT_PREFIX + key;
	}

	public static String allKey() {
		return KEY_ALL;
	}

	public static String clientKey(String clientId) {
		return clientId;
	}
}
