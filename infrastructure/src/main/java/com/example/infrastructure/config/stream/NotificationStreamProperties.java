package com.example.infrastructure.config.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.stream")
public record NotificationStreamProperties(
	String key,
	String consumerGroup,
	String consumerName,
	int pollInterval,
	int batchSize,
	String deadLetterKey,
	String waitKey,
	int maxRetryCount,
	int retryBaseDelayMillis,
	int waitPollIntervalMillis,
	int waitBatchSize
) {

	private static final int DEFAULT_MAX_RETRY_COUNT = 3;
	private static final int DEFAULT_RETRY_BASE_DELAY_MILLIS = 5000;
	private static final int DEFAULT_WAIT_BATCH_SIZE = 100;

	public String resolveKey(StreamKeyType streamKeyType) {
		String explicitKey = switch (streamKeyType) {
			case WORK -> null;
			case DEAD_LETTER -> deadLetterKey;
			case WAIT -> waitKey;
		};

		return streamKeyType.resolve(key, explicitKey);
	}

	public int resolveMaxRetryCount() {
		return maxRetryCount > 0 ? maxRetryCount : DEFAULT_MAX_RETRY_COUNT;
	}

	public int resolveRetryBaseDelayMillis() {
		return retryBaseDelayMillis > 0 ? retryBaseDelayMillis : DEFAULT_RETRY_BASE_DELAY_MILLIS;
	}

	public long calculateRetryDelayMillis(int retryCount) {
		return (long)resolveRetryBaseDelayMillis() * (1L << Math.min(retryCount, 10));
	}

	public int resolveWaitBatchSize() {
		return waitBatchSize > 0 ? waitBatchSize : DEFAULT_WAIT_BATCH_SIZE;
	}
}
