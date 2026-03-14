package com.example.application.port.in;

import com.example.application.port.in.result.NotificationStatsResult;

public interface AdminNotificationStatsUseCase {

	NotificationStatsResult getStats();

	NotificationStatsResult getStatsByClientId(String clientId);
}
