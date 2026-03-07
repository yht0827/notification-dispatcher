package com.example.mock.service.dto;

public record MockFailResult(
	String requestId,
	String channelType,
	String errorCode,
	String message,
	long startedAtMillis
) {

	private static final String UNKNOWN = "UNKNOWN";

	public static MockFailResult unknown(String errorCode, String message, long startedAtMillis) {
		return new MockFailResult(UNKNOWN, UNKNOWN, errorCode, message, startedAtMillis);
	}
}
