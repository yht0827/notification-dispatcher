package com.example.infrastructure.sender.mock.http;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Map;

import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRateLimitException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import feign.Response;
import feign.codec.ErrorDecoder;

public class MockApiErrorDecoder implements ErrorDecoder {

	@Override
	public Exception decode(String methodKey, Response response) {
		int status = response.status();
		String message = resolveMessage(response);

		if (status == 429) {
			Long retryAfterMillis = resolveRetryAfterMillis(response.headers());
			return new MockApiRateLimitException("외부 API Rate Limit(429): " + message, retryAfterMillis);
		}

		if (status >= 500) {
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

	private Long resolveRetryAfterMillis(Map<String, Collection<String>> headers) {
		if (headers == null || headers.isEmpty()) {
			return null;
		}

		for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
			if (!"Retry-After".equalsIgnoreCase(entry.getKey())) {
				continue;
			}
			for (String value : entry.getValue()) {
				Long retryAfterMillis = parseRetryAfter(value);
				if (retryAfterMillis != null) {
					return retryAfterMillis;
				}
			}
		}
		return null;
	}

	private Long parseRetryAfter(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		String trimmedValue = value.trim();
		try {
			return Long.parseLong(trimmedValue) * 1000L;
		} catch (NumberFormatException ignored) {
		}

		try {
			OffsetDateTime retryAt = OffsetDateTime.parse(trimmedValue,
				java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
			long delayMillis = retryAt.toInstant().toEpochMilli() - System.currentTimeMillis();
			return delayMillis > 0 ? delayMillis : null;
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}
}
