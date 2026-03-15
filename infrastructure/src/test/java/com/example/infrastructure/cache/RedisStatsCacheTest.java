package com.example.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.example.application.port.in.result.NotificationStatsResult;

@ExtendWith(MockitoExtension.class)
class RedisStatsCacheTest {

	@Mock
	private RedisTemplate<String, Object> redisTemplate;

	@Mock
	private ValueOperations<String, Object> valueOperations;

	private RedisStatsCache cache;

	@BeforeEach
	void setUp() {
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		cache = new RedisStatsCache(redisTemplate, new CacheProperties(true, 30));
	}

	@Test
	@DisplayName("캐시된 값이 있으면 loader를 호출하지 않는다")
	void get_returnsCachedValueWhenPresent() {
		NotificationStatsResult cached = new NotificationStatsResult(1, 2, 3, 4, 5, 15);
		@SuppressWarnings("unchecked")
		Supplier<NotificationStatsResult> loader = mock(Supplier.class);
		when(valueOperations.get("admin:stats:all")).thenReturn(cached);

		NotificationStatsResult result = cache.get(RedisStatsCache.allKey(), loader);

		assertThat(result).isSameAs(cached);
		verify(loader, never()).get();
		verify(valueOperations, never()).set(any(String.class), any(), any(Long.class), eq(TimeUnit.SECONDS));
	}

	@Test
	@DisplayName("캐시 미스면 loader 결과를 TTL과 함께 저장한다")
	void get_loadsAndStoresValueWhenMissing() {
		NotificationStatsResult loaded = new NotificationStatsResult(0, 1, 2, 3, 4, 10);
		@SuppressWarnings("unchecked")
		Supplier<NotificationStatsResult> loader = mock(Supplier.class);
		when(valueOperations.get("admin:stats:client:client-1")).thenReturn(null);
		when(loader.get()).thenReturn(loaded);

		NotificationStatsResult result = cache.get(RedisStatsCache.clientKey("client-1"), loader);

		assertThat(result).isSameAs(loaded);
		verify(loader).get();
		verify(valueOperations).set("admin:stats:client:client-1", loaded, 30L, TimeUnit.SECONDS);
	}

	@Test
	@DisplayName("특정 캐시 키를 삭제한다")
	void evict_deletesTranslatedKey() {
		cache.evict(RedisStatsCache.clientKey("client-2"));

		verify(redisTemplate).delete("admin:stats:client:client-2");
	}

	@Test
	@DisplayName("관리자 통계 캐시 전체 키가 있으면 모두 삭제한다")
	void evictAll_deletesAllMatchingKeys() {
		Set<String> keys = Set.of("admin:stats:all", "admin:stats:client:client-1");
		when(redisTemplate.keys("admin:stats:*")).thenReturn(keys);

		cache.evictAll();

		verify(redisTemplate).delete(keys);
	}

	@Test
	@DisplayName("관리자 통계 캐시 전체 키가 없으면 삭제를 건너뛴다")
	void evictAll_skipsDeleteWhenNoKeys() {
		when(redisTemplate.keys("admin:stats:*")).thenReturn(Set.of());

		cache.evictAll();

		verify(redisTemplate, never()).delete(any(Set.class));
	}
}
