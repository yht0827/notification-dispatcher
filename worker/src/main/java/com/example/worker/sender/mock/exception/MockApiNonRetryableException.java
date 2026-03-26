package com.example.worker.sender.mock.exception;

public class MockApiNonRetryableException extends RuntimeException {

	public MockApiNonRetryableException(String message) {
		super(message);
	}

}
