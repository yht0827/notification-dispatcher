package com.example.application.port.in.result;

import java.util.Map;

public record NotificationStatsResult(
	long pending,
	long sending,
	long sent,
	long failed,
	long canceled,
	long total
) {
	public static NotificationStatsResult from(Map<String, Long> countByStatus) {
		long pending = countByStatus.getOrDefault("PENDING", 0L);
		long sending = countByStatus.getOrDefault("SENDING", 0L);
		long sent = countByStatus.getOrDefault("SENT", 0L);
		long failed = countByStatus.getOrDefault("FAILED", 0L);
		long canceled = countByStatus.getOrDefault("CANCELED", 0L);
		return new NotificationStatsResult(pending, sending, sent, failed, canceled,
			pending + sending + sent + failed + canceled);
	}
}
