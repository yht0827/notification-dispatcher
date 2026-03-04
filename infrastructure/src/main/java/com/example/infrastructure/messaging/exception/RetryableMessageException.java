package com.example.infrastructure.messaging.exception;

public class RetryableMessageException extends RuntimeException {

	public RetryableMessageException(String message) {
		super(message);
	}

	public RetryableMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
