package com.example.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache.stats")
public record CacheProperties(
	boolean enabled,
	int ttlSeconds
) {
	public int resolveTtlSeconds() {
		return ttlSeconds > 0 ? ttlSeconds : 60;
	}
}
