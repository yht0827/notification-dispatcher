package com.example.infrastructure.sender.mock.exception;

public class MockApiRetryableException extends RuntimeException {

	public MockApiRetryableException(String message) {
		super(message);
	}

	public MockApiRetryableException(String message, Throwable cause) {
		super(message, cause);
	}

}
