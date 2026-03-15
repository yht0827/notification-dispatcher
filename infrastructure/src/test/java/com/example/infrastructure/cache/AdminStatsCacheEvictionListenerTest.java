package com.example.infrastructure.cache;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.event.AdminStatsChangedEvent;

@ExtendWith(MockitoExtension.class)
class AdminStatsCacheEvictionListenerTest {

	@Mock
	private RedisStatsCache cache;

	@Test
	@DisplayName("관리자 통계 변경 이벤트가 오면 전체 캐시를 비운다")
	void onAdminStatsChanged_evictsAllCache() {
		AdminStatsCacheEvictionListener listener = new AdminStatsCacheEvictionListener(cache);

		listener.onAdminStatsChanged(new AdminStatsChangedEvent());

		verify(cache).evictAll();
	}
}
