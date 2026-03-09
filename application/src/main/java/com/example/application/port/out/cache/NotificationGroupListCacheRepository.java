package com.example.application.port.out.cache;

import java.util.List;
import java.util.Optional;

import com.example.application.port.in.result.NotificationGroupResult;

public interface NotificationGroupListCacheRepository {

	boolean enabled();

	Optional<List<NotificationGroupResult>> getLatest(String clientId);

	void putLatest(String clientId, List<NotificationGroupResult> groups);

	void evictLatest(String clientId);

	int latestLimit();
}
