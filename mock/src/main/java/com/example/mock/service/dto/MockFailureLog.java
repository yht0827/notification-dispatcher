package com.example.mock.service.dto;

import com.example.mock.api.ChannelType;

public record MockFailureLog(
	String requestId,
	ChannelType channelType,
	String receiver,
	int messageLength,
	int httpStatus,
	long latencyMs
) {

	public static MockFailureLog unknown(int httpStatus, long latencyMs) {
		return new MockFailureLog("UNKNOWN", ChannelType.UNKNOWN, "UNKNOWN", 0, httpStatus, latencyMs);
	}
}
