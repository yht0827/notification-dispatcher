package com.example.infrastructure.config.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.rabbitmq")
public record NotificationRabbitProperties(
	String workQueue,
	String workExchange,
	String waitQueue,
	String dlqQueue,
	String dlqExchange,
	int maxRetryCount,
	int retryBaseDelayMillis,
	int concurrency,
	int maxConcurrency
) {

	private static final int DEFAULT_MAX_RETRY_COUNT = 3;
	private static final int DEFAULT_RETRY_BASE_DELAY_MILLIS = 5000;
	private static final int DEFAULT_CONCURRENCY = 1;
	private static final int DEFAULT_MAX_CONCURRENCY = 10;
	private static final int MAX_RETRY_BACKOFF_SHIFT = 10;
	private static final String WAIT_EXCHANGE_SUFFIX = ".exchange";

	public int resolveMaxRetryCount() {
		return maxRetryCount > 0 ? maxRetryCount : DEFAULT_MAX_RETRY_COUNT;
	}

	public int resolveRetryBaseDelayMillis() {
		return retryBaseDelayMillis > 0 ? retryBaseDelayMillis : DEFAULT_RETRY_BASE_DELAY_MILLIS;
	}

	public long calculateRetryDelayMillis(int retryCount) {
		int normalizedRetryCount = Math.max(retryCount, 0);
		int cappedShift = Math.min(normalizedRetryCount, MAX_RETRY_BACKOFF_SHIFT);
		return (long)resolveRetryBaseDelayMillis() * (1L << cappedShift);
	}

	public int resolveConcurrency() {
		return concurrency > 0 ? concurrency : DEFAULT_CONCURRENCY;
	}

	public int resolveMaxConcurrency() {
		int resolvedMaxConcurrency = maxConcurrency > 0 ? maxConcurrency : DEFAULT_MAX_CONCURRENCY;
		return Math.max(resolvedMaxConcurrency, resolveConcurrency());
	}

	public int resolvePrefetchCount() {
		return resolveConcurrency();
	}

	public String workRoutingKey() {
		return workQueue;
	}

	public String waitRoutingKey() {
		return waitQueue;
	}

	public String waitExchange() {
		return waitQueue + WAIT_EXCHANGE_SUFFIX;
	}
}
