package com.example.infrastructure.messaging.exception;

public class RetryableMessageException extends RuntimeException {

	private final Long retryDelayMillis;

	public RetryableMessageException(String message) {
		super(message);
		this.retryDelayMillis = null;
	}

	public RetryableMessageException(String message, Throwable cause) {
		super(message, cause);
		this.retryDelayMillis = null;
	}

	public RetryableMessageException(String message, Long retryDelayMillis) {
		super(message);
		this.retryDelayMillis = normalizeRetryDelayMillis(retryDelayMillis);
	}

	public RetryableMessageException(String message, Throwable cause, Long retryDelayMillis) {
		super(message, cause);
		this.retryDelayMillis = normalizeRetryDelayMillis(retryDelayMillis);
	}

	public Long retryDelayMillis() {
		return retryDelayMillis;
	}

	private static Long normalizeRetryDelayMillis(Long retryDelayMillis) {
		return retryDelayMillis != null && retryDelayMillis > 0 ? retryDelayMillis : null;
	}
}
