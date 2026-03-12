package com.example.infrastructure.archive;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive")
public record ArchiveProperties(
	boolean enabled,
	boolean runOnStartup,
	int batchSize,
	int retentionDays,
	String cron,
	String partitionCron
) {

	private static final int DEFAULT_BATCH_SIZE = 1000;
	private static final int DEFAULT_RETENTION_DAYS = 7;

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public int resolveRetentionDays() {
		return retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
	}

}
