package com.example.mock.service.dto;

public record MockFailureLog(
	String requestId,
	String channelType,
	String receiver,
	int messageLength,
	int httpStatus,
	long latencyMs
) {

	private static final String UNKNOWN = "UNKNOWN";

	public static MockFailureLog unknown(int httpStatus, long latencyMs) {
		return new MockFailureLog(UNKNOWN, UNKNOWN, UNKNOWN, 0, httpStatus, latencyMs);
	}
}
