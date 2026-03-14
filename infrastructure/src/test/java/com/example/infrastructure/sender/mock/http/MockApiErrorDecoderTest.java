package com.example.infrastructure.sender.mock.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRateLimitException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import feign.Response;

class MockApiErrorDecoderTest {

	private final MockApiErrorDecoder decoder = new MockApiErrorDecoder();

	@Test
	@DisplayName("5xx 응답은 retryable 예외로 변환한다")
	void decode_returnsRetryableForServerError() {
		Response response = responseWithStatus(503, null);

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception)
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("재시도 가능 오류(503)");
	}

	@Test
	@DisplayName("4xx 응답은 non-retryable 예외로 변환한다")
	void decode_returnsNonRetryableForClientError() {
		Response response = responseWithStatus(400, null);

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception)
			.isInstanceOf(MockApiNonRetryableException.class)
			.hasMessageContaining("재시도 불가 오류(400)");
	}

	@Test
	@DisplayName("429 응답은 rate-limit 예외로 분리하고 Retry-After를 파싱한다")
	void decode_returnsRateLimitExceptionFor429() {
		Response response = responseWithStatus(429, null, Map.of("Retry-After", List.of("15")));

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception).isInstanceOf(MockApiRateLimitException.class);
		assertThat(((MockApiRateLimitException)exception).retryAfterMillis()).isEqualTo(15_000L);
		assertThat(exception).hasMessageContaining("Rate Limit(429)");
	}

	@Test
	@DisplayName("429 Retry-After RFC1123 날짜 형식도 파싱한다")
	void decode_parsesRetryAfterDateHeaderFor429() {
		String retryAfter = OffsetDateTime.now(ZoneOffset.UTC)
			.plusSeconds(30)
			.format(DateTimeFormatter.RFC_1123_DATE_TIME);
		Response response = responseWithStatus(429, null, Map.of("Retry-After", List.of(retryAfter)));

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception).isInstanceOf(MockApiRateLimitException.class);
		assertThat(((MockApiRateLimitException)exception).retryAfterMillis()).isBetween(1_000L, 30_000L);
	}

	@Test
	@DisplayName("HTTP reason이 있으면 예외 메시지에 포함한다")
	void decode_includesReasonPhrase() {
		Response response = responseWithStatus(500, "mock backend down");

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception)
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("mock backend down");
	}

	private Response responseWithStatus(int status, String reason) {
		return responseWithStatus(status, reason, Map.of());
	}

	private Response responseWithStatus(int status, String reason, Map<String, List<String>> headers) {
		Response response = mock(Response.class);
		when(response.status()).thenReturn(status);
		when(response.reason()).thenReturn(reason);
		when(response.headers()).thenReturn((Map)headers);
		return response;
	}
}
