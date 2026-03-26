package com.example.worker.sender.mock.exception;

public class MockApiRateLimitException extends RuntimeException {

	private final Long retryAfterMillis;

	public MockApiRateLimitException(String message, Long retryAfterMillis) {
		super(message);
		this.retryAfterMillis = normalizeRetryAfterMillis(retryAfterMillis);
	}

	public Long retryAfterMillis() {
		return retryAfterMillis;
	}

	private static Long normalizeRetryAfterMillis(Long retryAfterMillis) {
		return retryAfterMillis != null && retryAfterMillis > 0 ? retryAfterMillis : null;
	}
}
