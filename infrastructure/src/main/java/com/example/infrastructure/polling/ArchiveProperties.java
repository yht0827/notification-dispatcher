package com.example.infrastructure.polling;

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
	private static final String DEFAULT_CRON = "0 0 0 * * *";
	private static final String DEFAULT_PARTITION_CRON = "0 5 0 1 * *";

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public int resolveRetentionDays() {
		return retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
	}

	public boolean shouldRunOnStartup() {
		return runOnStartup;
	}

	public String resolveCron() {
		return cron != null && !cron.isBlank() ? cron : DEFAULT_CRON;
	}

	public String resolvePartitionCron() {
		return partitionCron != null && !partitionCron.isBlank()
			? partitionCron
			: DEFAULT_PARTITION_CRON;
	}
}
