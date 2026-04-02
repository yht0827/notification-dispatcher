package com.example.worker.support;

import com.example.worker.config.rabbitmq.NotificationRabbitProperties;

public final class NotificationRabbitPropertiesFixtures {

	private NotificationRabbitPropertiesFixtures() {
	}

	public static NotificationRabbitProperties defaultProperties() {
		return properties(3, null, 0.0d);
	}

	public static NotificationRabbitProperties propertiesWithMaxRetryCount(int maxRetryCount) {
		return properties(maxRetryCount, null, 0.0d);
	}

	public static NotificationRabbitProperties properties(Boolean listenerVirtualThreads, double retryJitterFactor) {
		return properties(3, listenerVirtualThreads, retryJitterFactor);
	}

	private static NotificationRabbitProperties properties(int maxRetryCount, Boolean listenerVirtualThreads,
		double retryJitterFactor) {
		return new NotificationRabbitProperties(
			"notification.work",
			"notification.work.exchange",
			"notification.wait",
			"notification.dlq",
			"notification.dlq.exchange",
			maxRetryCount,
			5000,
			1,
			10,
			1,
			listenerVirtualThreads,
			retryJitterFactor
		);
	}
}
