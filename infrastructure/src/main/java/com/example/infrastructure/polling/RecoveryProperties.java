package com.example.infrastructure.polling;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recovery")
public record RecoveryProperties(
	int batchSize,
	long thresholdMinutes,
	long claimMinIdleMinutes
) {

	private static final int DEFAULT_BATCH_SIZE = 100;
	private static final long DEFAULT_THRESHOLD_MINUTES = 5;
	private static final long DEFAULT_CLAIM_MIN_IDLE_MINUTES = 5;

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public long resolveThresholdMinutes() {
		return thresholdMinutes > 0 ? thresholdMinutes : DEFAULT_THRESHOLD_MINUTES;
	}

	public Duration resolveClaimMinIdleTime() {
		long minutes = claimMinIdleMinutes > 0 ? claimMinIdleMinutes : DEFAULT_CLAIM_MIN_IDLE_MINUTES;
		return Duration.ofMinutes(minutes);
	}
}
