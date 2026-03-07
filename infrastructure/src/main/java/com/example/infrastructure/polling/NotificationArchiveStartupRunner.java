package com.example.infrastructure.polling;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class NotificationArchiveStartupRunner implements ApplicationRunner {

	private final NotificationArchiveService notificationArchiveService;

	@Override
	public void run(ApplicationArguments args) {
		NotificationArchiveService.ArchiveRunResult result = notificationArchiveService.archiveExpiredData();
		log.info("Archive startup run 완료: cutoff={}, notifications={}, groups={}",
			result.cutoff(), result.archivedNotifications(), result.archivedGroups());
	}
}
