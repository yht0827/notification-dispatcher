package com.example.worker.sender.mock.dto;

public record MockApiSendSuccessResponse(
	String result,
	String requestId,
	String channelType,
	String processedAt,
	long latencyMs
) {
}
