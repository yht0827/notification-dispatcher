package com.example.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache.stats")
public record CacheProperties(
	boolean enabled,
	int l1TtlSeconds,
	int l2TtlSeconds
) {
	public int resolveL1TtlSeconds() {
		return l1TtlSeconds > 0 ? l1TtlSeconds : 10;
	}

	public int resolveL2TtlSeconds() {
		return l2TtlSeconds > 0 ? l2TtlSeconds : 60;
	}
}
