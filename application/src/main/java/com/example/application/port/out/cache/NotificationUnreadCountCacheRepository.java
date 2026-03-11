package com.example.application.port.out.cache;

import java.util.Optional;

public interface NotificationUnreadCountCacheRepository {

	boolean enabled();

	Optional<Long> get(String clientId, String receiver);

	void put(String clientId, String receiver, long unreadCount);

	void evict(String clientId, String receiver);

	void increment(String clientId, String receiver);

	void decrement(String clientId, String receiver);
}
