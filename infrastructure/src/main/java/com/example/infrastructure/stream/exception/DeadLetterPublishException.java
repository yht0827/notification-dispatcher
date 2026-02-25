package com.example.infrastructure.stream.exception;

public class DeadLetterPublishException extends RuntimeException {

	public DeadLetterPublishException(String message) {
		super(message);
	}

	public DeadLetterPublishException(String message, Throwable cause) {
		super(message, cause);
	}
}
