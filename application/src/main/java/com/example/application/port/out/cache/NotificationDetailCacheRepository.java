package com.example.application.port.out.cache;

import java.util.Optional;

import com.example.application.port.in.result.NotificationResult;

public interface NotificationDetailCacheRepository {

	Optional<NotificationResult> get(Long notificationId);

	void put(Long notificationId, NotificationResult detail);

	void evict(Long notificationId);
}
