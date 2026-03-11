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
	Boolean listenerVirtualThreads,// null이면 spring.threads.virtual.enabled 상속
	boolean batchListenerEnabled,  // 배치 리스너 ON/OFF
	int batchSize,                 // 배치 리스너 크기
	int batchReceiveTimeoutMillis, // 배치 수집 대기 시간(ms)
	double retryJitterFactor       // 재시도 지연 랜덤화 비율
) {

	private static final int DEFAULT_RETRY_BASE_DELAY_MILLIS = 5000;
	private static final int DEFAULT_CONCURRENCY = 1;
	private static final int DEFAULT_MAX_CONCURRENCY = 10;
	private static final int DEFAULT_BATCH_SIZE = 50;
	private static final int DEFAULT_BATCH_RECEIVE_TIMEOUT_MILLIS = 200;
	private static final double DEFAULT_RETRY_JITTER_FACTOR = 0.0d;
	private static final int MAX_RETRY_BACKOFF_SHIFT = 10;
	private static final String WAIT_EXCHANGE_SUFFIX = ".exchange";

	public int resolveRetryBaseDelayMillis() {
		return retryBaseDelayMillis > 0 ? retryBaseDelayMillis : DEFAULT_RETRY_BASE_DELAY_MILLIS;
	}

	public long calculateRetryDelayMillis(int retryCount, Long retryDelayMillis) {
		if (retryDelayMillis != null && retryDelayMillis > 0) {
			return retryDelayMillis;
		}

		int normalizedRetryCount = Math.max(retryCount, 0);
		int cappedShift = Math.min(normalizedRetryCount, MAX_RETRY_BACKOFF_SHIFT);
		long baseDelayMillis = (long)resolveRetryBaseDelayMillis() * (1L << cappedShift);
		return applyJitter(baseDelayMillis);
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
		return resolveConcurrency();
	}

	public boolean resolveListenerVirtualThreads(boolean appVirtualThreadsEnabled) {
		return listenerVirtualThreads != null ? listenerVirtualThreads : appVirtualThreadsEnabled;
	}

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}

	public long resolveBatchReceiveTimeoutMillis() {
		return batchReceiveTimeoutMillis > 0
			? batchReceiveTimeoutMillis
			: DEFAULT_BATCH_RECEIVE_TIMEOUT_MILLIS;
	}

	public double resolveRetryJitterFactor() {
		if (retryJitterFactor <= 0) {
			return DEFAULT_RETRY_JITTER_FACTOR;
		}
		return Math.min(retryJitterFactor, 1.0d);
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

	private long applyJitter(long delayMillis) {
		double jitterFactor = resolveRetryJitterFactor();
		if (jitterFactor <= 0.0d) {
			return delayMillis;
		}

		double minMultiplier = Math.max(0.0d, 1.0d - jitterFactor);
		double maxMultiplier = 1.0d + jitterFactor;
		double multiplier = java.util.concurrent.ThreadLocalRandom.current().nextDouble(minMultiplier, maxMultiplier);
		return Math.max(1L, Math.round(delayMillis * multiplier));
	}
}
