package com.example.mock.service;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class MockFailureException extends RuntimeException {

	private final HttpStatus status;
	private final String errorCode;
	private final String requestId;
	private final String channelType;
	private final String receiver;
	private final int messageLength;
	private final long startedAtMillis;
	private final Integer retryAfterSeconds;

	public MockFailureException(
		HttpStatus status,
		String errorCode,
		String message,
		String requestId,
		String channelType,
		String receiver,
		int messageLength,
		long startedAtMillis,
		Integer retryAfterSeconds
	) {
		super(message);
		this.status = status;
		this.errorCode = errorCode;
		this.requestId = requestId;
		this.channelType = channelType;
		this.receiver = receiver;
		this.messageLength = messageLength;
		this.startedAtMillis = startedAtMillis;
		this.retryAfterSeconds = retryAfterSeconds;
	}
}
