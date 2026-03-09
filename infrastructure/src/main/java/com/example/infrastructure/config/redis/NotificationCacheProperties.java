package com.example.infrastructure.config.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.cache")
public record NotificationCacheProperties(
	Duration unreadCountTtl,
	Duration groupDetailTtl
) {

	public NotificationCacheProperties {
		if (unreadCountTtl == null) {
			unreadCountTtl = Duration.ofSeconds(30);
		}
		if (groupDetailTtl == null) {
			groupDetailTtl = Duration.ofSeconds(30);
		}
	}
}
