package com.example.mock.service.dto;

import com.example.mock.api.ChannelType;

public record MockFailResult(
	String requestId,
	ChannelType channelType,
	String errorCode,
	String message,
	long startedAtMillis
) {

	public static MockFailResult unknown(String errorCode, String message, long startedAtMillis) {
		return new MockFailResult("UNKNOWN", ChannelType.UNKNOWN, errorCode, message, startedAtMillis);
	}
}
