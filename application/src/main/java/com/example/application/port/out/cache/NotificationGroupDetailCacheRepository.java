package com.example.application.port.out.cache;

import java.util.Optional;

import com.example.application.port.in.result.NotificationGroupDetailResult;

public interface NotificationGroupDetailCacheRepository {

	Optional<NotificationGroupDetailResult> get(Long groupId);

	void put(Long groupId, NotificationGroupDetailResult detail);

	void evict(Long groupId);
}
