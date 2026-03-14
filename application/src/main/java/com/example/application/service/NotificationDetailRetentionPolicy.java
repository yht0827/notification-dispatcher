package com.example.application.service;

import java.time.LocalDateTime;

final class NotificationDetailRetentionPolicy {

	private static final long DETAIL_RETENTION_DAYS = 7;

	private NotificationDetailRetentionPolicy() {
	}

	static LocalDateTime detailFrom(LocalDateTime now) {
		return now.minusDays(DETAIL_RETENTION_DAYS);
	}

	static boolean isWithinRetention(LocalDateTime createdAt, LocalDateTime from) {
		return createdAt != null && !createdAt.isBefore(from);
	}
}
