package com.example.infrastructure.messaging.exception;

public class NonRetryableMessageException extends RuntimeException {

	public NonRetryableMessageException(String message) {
		super(message);
	}

	public NonRetryableMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
