package com.example.infrastructure.stream.exception;

public class NonRetryableStreamMessageException extends RuntimeException {

	public NonRetryableStreamMessageException(String message) {
		super(message);
	}

	public NonRetryableStreamMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
