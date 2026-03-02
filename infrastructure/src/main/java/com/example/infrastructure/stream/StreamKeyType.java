package com.example.infrastructure.stream;

public enum StreamKeyType {
	WORK(null),
	DEAD_LETTER("-dlq"),
	WAIT("-wait");

	private static final String DEFAULT_WORK_KEY = "notification-stream";

	private final String defaultSuffix;

	StreamKeyType(String defaultSuffix) {
		this.defaultSuffix = defaultSuffix;
	}

	public String resolve(String configuredWorkKey, String explicitKey) {
		String workKey = hasText(configuredWorkKey) ? configuredWorkKey : DEFAULT_WORK_KEY;

		if (this == WORK) {
			return workKey;
		}

		if (hasText(explicitKey)) {
			return explicitKey;
		}

		return workKey + defaultSuffix;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
