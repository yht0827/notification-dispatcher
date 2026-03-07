package com.example.mock.service.dto;

import com.example.mock.api.MockSendRequest;

public record MockSendContext(
	String requestId,
	String channelType,
	String receiver,
	String message,
	long startedAtMillis
) {

	private static final String UNKNOWN = "UNKNOWN";

	public static MockSendContext from(MockSendRequest request, long startedAtMillis) {
		if (request == null) {
			return new MockSendContext(UNKNOWN, UNKNOWN, UNKNOWN, "", startedAtMillis);
		}

		String channelType = request.channelType() == null ? UNKNOWN : request.channelType().name();
		return new MockSendContext(
			safeText(request.requestId()),
			channelType,
			safeText(request.receiver()),
			request.message(),
			startedAtMillis
		);
	}

	public int messageLength() {
		return message == null ? 0 : message.length();
	}

	public String messageOrEmpty() {
		return message == null ? "" : message;
	}

	private static String safeText(String value) {
		return value == null || value.isBlank() ? UNKNOWN : value;
	}
}
