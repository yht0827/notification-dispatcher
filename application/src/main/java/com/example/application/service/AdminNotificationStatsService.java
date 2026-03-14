package com.example.application.service;

import org.springframework.stereotype.Service;

import com.example.application.port.in.AdminNotificationStatsUseCase;
import com.example.application.port.in.result.NotificationStatsResult;
import com.example.application.port.out.repository.NotificationStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminNotificationStatsService implements AdminNotificationStatsUseCase {

	private final NotificationStatsRepository statsRepository;

	@Override
	public NotificationStatsResult getStats() {
		return NotificationStatsResult.from(statsRepository.countByStatus());
	}

	@Override
	public NotificationStatsResult getStatsByClientId(String clientId) {
		return NotificationStatsResult.from(statsRepository.countByStatusAndClientId(clientId));
	}
}
