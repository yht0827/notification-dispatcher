package com.example.infrastructure.cache;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.AdminNotificationStatsUseCase;
import com.example.application.port.in.result.NotificationStatsResult;
import com.example.application.port.out.repository.NotificationStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@Primary
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CachedAdminNotificationStatsService implements AdminNotificationStatsUseCase {

	private final NotificationStatsRepository statsRepository;
	private final RedisStatsCache cache;
	private final CacheProperties cacheProperties;

	@Override
	public NotificationStatsResult getStats() {
		if (!cacheProperties.enabled()) {
			return NotificationStatsResult.from(statsRepository.countByStatus());
		}
		return cache.get(RedisStatsCache.allKey(),
			() -> NotificationStatsResult.from(statsRepository.countByStatus()));
	}

	@Override
	public NotificationStatsResult getStatsByClientId(String clientId) {
		if (!cacheProperties.enabled()) {
			return NotificationStatsResult.from(statsRepository.countByStatusAndClientId(clientId));
		}
		return cache.get(RedisStatsCache.clientKey(clientId),
			() -> NotificationStatsResult.from(statsRepository.countByStatusAndClientId(clientId)));
	}
}
