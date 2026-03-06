package com.example.infrastructure.config.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.rabbitmq")
public record NotificationRabbitProperties(
	String workQueue,              // "notification.work"
	String workExchange,           // "notification.work.exchange"
	String waitQueue,              // "notification.wait"
	String dlqQueue,               // "notification.dlq"
	String dlqExchange,            // "notification.dlq.exchange"
	int maxRetryCount,             // 최대 3회
	int retryBaseDelayMillis,      // 기본 5000ms
	int concurrency,               // 초기 1
	int maxConcurrency,            // 최대 10
	int prefetch,                  // 기본 concurrency와 동일
	boolean batchListenerEnabled,  // 배치 리스너 ON/OFF
	int batchSize,                 // 배치 리스너 크기
	int batchReceiveTimeoutMillis  // 배치 수집 대기 시간(ms)
) {

	private static final int DEFAULT_MAX_RETRY_COUNT = 3;
	private static final int DEFAULT_RETRY_BASE_DELAY_MILLIS = 5000;
	private static final int DEFAULT_CONCURRENCY = 1;
	private static final int DEFAULT_MAX_CONCURRENCY = 10;
	private static final int DEFAULT_PREFETCH_COUNT = 1;
	private static final int DEFAULT_BATCH_SIZE = 50;
	private static final int DEFAULT_BATCH_RECEIVE_TIMEOUT_MILLIS = 200;
	private static final int MAX_RETRY_BACKOFF_SHIFT = 10;
	private static final String WAIT_EXCHANGE_SUFFIX = ".exchange";

	public int resolveMaxRetryCount() {
		return maxRetryCount > 0 ? maxRetryCount : DEFAULT_MAX_RETRY_COUNT;
	}

	public int resolveRetryBaseDelayMillis() {
		return retryBaseDelayMillis > 0 ? retryBaseDelayMillis : DEFAULT_RETRY_BASE_DELAY_MILLIS;
	}

	// 지수 백오프 계산
	public long calculateRetryDelayMillis(int retryCount) {
		int normalizedRetryCount = Math.max(retryCount, 0);
		int cappedShift = Math.min(normalizedRetryCount, MAX_RETRY_BACKOFF_SHIFT);
		return (long)resolveRetryBaseDelayMillis() * (1L << cappedShift);  // = 5000 * 2^retryCount
	}

	public int resolveConcurrency() {
		return concurrency > 0 ? concurrency : DEFAULT_CONCURRENCY;
	}

	public int resolveMaxConcurrency() {
		int resolvedMaxConcurrency = maxConcurrency > 0 ? maxConcurrency : DEFAULT_MAX_CONCURRENCY;
		return Math.max(resolvedMaxConcurrency, resolveConcurrency());
	}

	public int resolvePrefetchCount() {
		if (prefetch > 0) {
			return prefetch;
		}
		int resolvedConcurrency = resolveConcurrency();
		return resolvedConcurrency > 0 ? resolvedConcurrency : DEFAULT_PREFETCH_COUNT;
	}

	public boolean resolveBatchListenerEnabled() {
		return batchListenerEnabled;
	}

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public long resolveBatchReceiveTimeoutMillis() {
		return batchReceiveTimeoutMillis > 0
			? batchReceiveTimeoutMillis
			: DEFAULT_BATCH_RECEIVE_TIMEOUT_MILLIS;
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
