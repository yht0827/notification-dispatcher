package com.example.infrastructure.stream.exception;

public class RetryableStreamMessageException extends RuntimeException {

	public RetryableStreamMessageException(String message) {
		super(message);
	}

	public RetryableStreamMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
