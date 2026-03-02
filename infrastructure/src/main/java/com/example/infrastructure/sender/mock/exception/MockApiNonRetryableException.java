package com.example.infrastructure.sender.mock.exception;

public class MockApiNonRetryableException extends RuntimeException {

	public MockApiNonRetryableException(String message) {
		super(message);
	}

}
