package com.example.infrastructure.archive;

import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NotificationArchiveScheduler {

	private final NotificationArchiveService notificationArchiveService;
	private final NotificationPartitionManager notificationPartitionManager;

	@Scheduled(cron = "${archive.cron:0 0 0 * * *}")
	public void archiveExpiredData() {
		notificationArchiveService.archiveExpiredData();
	}

	@Scheduled(cron = "${archive.partition-cron:0 5 0 1 * *}")
	public void ensureNextMonthPartitions() {
		notificationPartitionManager.ensureNextMonthPartitions();
	}
}
