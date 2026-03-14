package com.example.infrastructure.cache;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.example.application.port.in.result.NotificationStatsResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TwoLevelStatsCache {

	private static final String REDIS_KEY_ALL = "admin:stats:all";
	private static final String REDIS_KEY_CLIENT_PREFIX = "admin:stats:client:";

	private final RedisTemplate<String, Object> objectRedisTemplate;
	private final CacheProperties cacheProperties;

	private Cache<String, NotificationStatsResult> localCache;

	@PostConstruct
	public void init() {
		localCache = Caffeine.newBuilder()
			.expireAfterWrite(cacheProperties.resolveL1TtlSeconds(), TimeUnit.SECONDS)
			.maximumSize(500)
			.build();
	}

	public NotificationStatsResult get(String key, Supplier<NotificationStatsResult> loader) {
		// L1 check
		NotificationStatsResult l1 = localCache.getIfPresent(key);
		if (l1 != null) {
			return l1;
		}

		// L2 check
		String redisKey = toRedisKey(key);
		NotificationStatsResult l2 = (NotificationStatsResult) objectRedisTemplate.opsForValue().get(redisKey);
		if (l2 != null) {
			localCache.put(key, l2);
			return l2;
		}

		// DB load
		NotificationStatsResult result = loader.get();
		objectRedisTemplate.opsForValue().set(redisKey, result, cacheProperties.resolveL2TtlSeconds(), TimeUnit.SECONDS);
		localCache.put(key, result);
		return result;
	}

	public void evict(String key) {
		localCache.invalidate(key);
		objectRedisTemplate.delete(toRedisKey(key));
	}

	public void evictAll() {
		localCache.invalidateAll();
		Set<String> keys = objectRedisTemplate.keys("admin:stats:*");
		if (keys != null && !keys.isEmpty()) {
			objectRedisTemplate.delete(keys);
		}
	}

	private String toRedisKey(String key) {
		if (key.equals(REDIS_KEY_ALL)) {
			return REDIS_KEY_ALL;
		}
		return REDIS_KEY_CLIENT_PREFIX + key;
	}

	public static String allKey() {
		return REDIS_KEY_ALL;
	}

	public static String clientKey(String clientId) {
		return clientId;
	}
}
