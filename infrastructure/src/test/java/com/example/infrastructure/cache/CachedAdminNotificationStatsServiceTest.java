package com.example.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.in.result.NotificationStatsResult;
import com.example.application.port.out.repository.NotificationStatsRepository;

@ExtendWith(MockitoExtension.class)
class CachedAdminNotificationStatsServiceTest {

	@Mock
	private NotificationStatsRepository statsRepository;

	@Mock
	private RedisStatsCache cache;

	@Test
	@DisplayName("캐시가 비활성화되면 전체 통계를 DB에서 직접 조회한다")
	void getStats_readsDirectlyWhenCacheDisabled() {
		CachedAdminNotificationStatsService service =
			new CachedAdminNotificationStatsService(statsRepository, cache, new CacheProperties(false, 60));
		when(statsRepository.countByStatus()).thenReturn(Map.of("SENT", 3L, "FAILED", 1L));

		NotificationStatsResult result = service.getStats();

		assertThat(result.sent()).isEqualTo(3L);
		assertThat(result.failed()).isEqualTo(1L);
		assertThat(result.total()).isEqualTo(4L);
		verify(cache, never()).get(any(), any());
	}

	@Test
	@DisplayName("캐시가 활성화되면 전체 통계를 캐시에서 조회한다")
	void getStats_usesCacheWhenEnabled() {
		CachedAdminNotificationStatsService service =
			new CachedAdminNotificationStatsService(statsRepository, cache, new CacheProperties(true, 60));
		NotificationStatsResult cached = new NotificationStatsResult(1, 2, 3, 4, 5, 15);
		when(cache.get(eq(RedisStatsCache.allKey()), any())).thenReturn(cached);

		NotificationStatsResult result = service.getStats();

		assertThat(result).isSameAs(cached);
		verify(statsRepository, never()).countByStatus();
	}

	@Test
	@DisplayName("캐시가 비활성화되면 clientId 통계를 DB에서 직접 조회한다")
	void getStatsByClientId_readsDirectlyWhenCacheDisabled() {
		CachedAdminNotificationStatsService service =
			new CachedAdminNotificationStatsService(statsRepository, cache, new CacheProperties(false, 60));
		when(statsRepository.countByStatusAndClientId("client-1")).thenReturn(Map.of("PENDING", 2L, "SENT", 4L));

		NotificationStatsResult result = service.getStatsByClientId("client-1");

		assertThat(result.pending()).isEqualTo(2L);
		assertThat(result.sent()).isEqualTo(4L);
		assertThat(result.total()).isEqualTo(6L);
		verify(cache, never()).get(any(), any());
	}

	@Test
	@DisplayName("캐시가 활성화되면 clientId 통계를 캐시에서 조회한다")
	void getStatsByClientId_usesCacheWhenEnabled() {
		CachedAdminNotificationStatsService service =
			new CachedAdminNotificationStatsService(statsRepository, cache, new CacheProperties(true, 60));
		NotificationStatsResult cached = new NotificationStatsResult(0, 0, 7, 1, 0, 8);
		when(cache.get(eq(RedisStatsCache.clientKey("client-2")), any())).thenReturn(cached);

		NotificationStatsResult result = service.getStatsByClientId("client-2");

		assertThat(result).isSameAs(cached);
		verify(statsRepository, never()).countByStatusAndClientId(any());
	}
}
