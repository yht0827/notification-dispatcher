package com.example.infrastructure.sender.mock.dto;

public record MockApiSendFailResponse(
	String result,
	String requestId,
	String channelType,
	String errorCode,
	String message,
	String processedAt,
	long latencyMs
) {
}
