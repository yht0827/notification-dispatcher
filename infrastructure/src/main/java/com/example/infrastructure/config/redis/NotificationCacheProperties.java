package com.example.infrastructure.config.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.cache")
public record NotificationCacheProperties(
	Boolean unreadCountEnabled,
	Duration unreadCountTtl
) {

	public NotificationCacheProperties {
		if (unreadCountEnabled == null) {
			unreadCountEnabled = true;
		}
		if (unreadCountTtl == null) {
			unreadCountTtl = Duration.ofSeconds(30);
		}
	}
}
