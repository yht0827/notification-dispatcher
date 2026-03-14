package com.example.infrastructure.archive;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive")
public record ArchiveProperties(
	boolean enabled,
	int batchSize,
	int retentionDays,
	String cron,
	String partitionCron,
	int partitionRetentionMonths
) {

	private static final int DEFAULT_BATCH_SIZE = 1000;
	private static final int DEFAULT_RETENTION_DAYS = 7;
	private static final int DEFAULT_PARTITION_RETENTION_MONTHS = 12;

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public int resolveRetentionDays() {
		return retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
	}

	public int resolvePartitionRetentionMonths() {
		return partitionRetentionMonths > 0 ? partitionRetentionMonths : DEFAULT_PARTITION_RETENTION_MONTHS;
	}

}
