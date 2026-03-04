package com.example.infrastructure.polling;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recovery")
public record RecoveryProperties(
	int batchSize,
	long thresholdMinutes
) {

	private static final int DEFAULT_BATCH_SIZE = 100;
	private static final long DEFAULT_THRESHOLD_MINUTES = 5;

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public long resolveThresholdMinutes() {
		return thresholdMinutes > 0 ? thresholdMinutes : DEFAULT_THRESHOLD_MINUTES;
	}
}
