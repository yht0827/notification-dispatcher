package com.example.worker.sender.mock.exception;

public class MockApiRetryableException extends RuntimeException {

	public MockApiRetryableException(String message) {
		super(message);
	}

	public MockApiRetryableException(String message, Throwable cause) {
		super(message, cause);
	}

}
