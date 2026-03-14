package com.example.application.port.out.repository;

import java.util.Map;

public interface NotificationStatsRepository {

	Map<String, Long> countByStatus();

	Map<String, Long> countByStatusAndClientId(String clientId);
}
