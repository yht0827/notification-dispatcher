package com.example.worker.config.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.rabbitmq")
public record NotificationRabbitProperties(
	String workQueue,              // "notification.work"
	String workExchange,           // "notification.work.exchange"
	String waitQueue,              // "notification.wait"
	String waitExchange,           // "notification.wait.exchange"
	String dlqQueue,               // "notification.dlq"
	String dlqExchange,            // "notification.dlq.exchange"
	int maxRetryCount,             // 최대 3회
	int retryBaseDelayMillis,      // 기본 5000ms
	int concurrency,               // 초기 1
	int maxConcurrency,            // 최대 10
	int prefetch,                  // 기본 concurrency와 동일
	Boolean listenerVirtualThreads,// null이면 spring.threads.virtual.enabled 상속
	double retryJitterFactor       // 재시도 지연 랜덤화 비율
) {

	public static final int DEFAULT_MAX_RETRY_COUNT = 3;
	private static final int DEFAULT_RETRY_BASE_DELAY_MILLIS = 5000;
	private static final int DEFAULT_CONCURRENCY = 1;
	private static final int DEFAULT_MAX_CONCURRENCY = 10;
	private static final double DEFAULT_RETRY_JITTER_FACTOR = 0.0d;

	public int resolveMaxRetryCount() {
		return maxRetryCount > 0 ? maxRetryCount : DEFAULT_MAX_RETRY_COUNT;
	}

	public int resolveRetryBaseDelayMillis() {
		return retryBaseDelayMillis > 0 ? retryBaseDelayMillis : DEFAULT_RETRY_BASE_DELAY_MILLIS;
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

	public double resolveRetryJitterFactor() {
		if (retryJitterFactor <= 0) {
			return DEFAULT_RETRY_JITTER_FACTOR;
		}
		return Math.min(retryJitterFactor, 1.0d);
	}
}
