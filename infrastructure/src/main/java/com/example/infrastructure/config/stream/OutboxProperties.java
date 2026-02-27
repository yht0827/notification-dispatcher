package com.example.infrastructure.config.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(int batchSize) {

	private static final int DEFAULT_BATCH_SIZE = 100;

	public int resolveBatchSize() {
		return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
	}
}
