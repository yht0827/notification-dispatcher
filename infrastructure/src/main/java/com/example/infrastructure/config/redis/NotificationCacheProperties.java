package com.example.infrastructure.config.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.cache")
public record NotificationCacheProperties(
	Duration unreadCountTtl
) {

	public NotificationCacheProperties {
		if (unreadCountTtl == null) {
			unreadCountTtl = Duration.ofSeconds(30);
		}
	}
}
