package com.example.infrastructure.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.application.port.out.event.AdminStatsChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminStatsCacheEvictionListener {

	private final RedisStatsCache cache;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAdminStatsChanged(AdminStatsChangedEvent event) {
		cache.evictAll();
		log.debug("어드민 통계 캐시 무효화 완료");
	}
}
