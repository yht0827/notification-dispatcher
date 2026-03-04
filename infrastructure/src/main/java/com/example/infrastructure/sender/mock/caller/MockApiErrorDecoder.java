package com.example.infrastructure.sender.mock.caller;

import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import feign.Response;
import feign.codec.ErrorDecoder;

public class MockApiErrorDecoder implements ErrorDecoder {

	@Override
	public Exception decode(String methodKey, Response response) {
		int status = response.status();
		String message = resolveMessage(response);

		if (status == 429 || status >= 500) {
			return new MockApiRetryableException("외부 API 재시도 가능 오류(" + status + "): " + message);
		}

		return new MockApiNonRetryableException("외부 API 재시도 불가 오류(" + status + "): " + message);
	}

	private String resolveMessage(Response response) {
		String reason = response.reason();
		if (reason != null && !reason.isBlank()) {
			return reason;
		}

		return switch (response.status()) {
			case 429 -> "Too Many Requests";
			case 503 -> "Service Unavailable";
			default -> "HTTP " + response.status();
		};
	}
}
