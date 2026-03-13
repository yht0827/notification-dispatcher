package com.example.infrastructure.archive;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archive.s3")
public record S3ArchiveProperties(
	boolean enabled,
	String bucket,
	String prefix,
	String region
) {
	public String resolvePrefix() {
		return (prefix != null && !prefix.isBlank()) ? prefix : "archive";
	}
}
