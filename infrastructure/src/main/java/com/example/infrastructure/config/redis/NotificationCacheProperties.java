package com.example.infrastructure.config.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.cache")
public record NotificationCacheProperties(
	Boolean unreadCountEnabled,
	Duration unreadCountTtl,
	Boolean groupDetailEnabled,
	Duration groupDetailTtl,
	Boolean notificationDetailEnabled,
	Duration notificationDetailTtl,
	Boolean groupListEnabled,
	Duration groupListTtl,
	Integer groupListLatestLimit
) {

	public NotificationCacheProperties {
		if (unreadCountEnabled == null) {
			unreadCountEnabled = true;
		}
		if (unreadCountTtl == null) {
			unreadCountTtl = Duration.ofSeconds(30);
		}
		if (groupDetailEnabled == null) {
			groupDetailEnabled = true;
		}
		if (groupDetailTtl == null) {
			groupDetailTtl = Duration.ofSeconds(30);
		}
		if (notificationDetailEnabled == null) {
			notificationDetailEnabled = true;
		}
		if (notificationDetailTtl == null) {
			notificationDetailTtl = Duration.ofSeconds(30);
		}
		if (groupListEnabled == null) {
			groupListEnabled = true;
		}
		if (groupListTtl == null) {
			groupListTtl = Duration.ofSeconds(30);
		}
		if (groupListLatestLimit == null || groupListLatestLimit < 1) {
			groupListLatestLimit = 60;
		}
	}
}
