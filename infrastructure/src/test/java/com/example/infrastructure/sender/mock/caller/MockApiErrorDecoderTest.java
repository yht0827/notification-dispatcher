package com.example.infrastructure.sender.mock.caller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
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
	@DisplayName("HTTP reason이 있으면 예외 메시지에 포함한다")
	void decode_includesReasonPhrase() {
		Response response = responseWithStatus(500, "mock backend down");

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception)
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("mock backend down");
	}

	private Response responseWithStatus(int status, String reason) {
		Response response = mock(Response.class);
		when(response.status()).thenReturn(status);
		when(response.reason()).thenReturn(reason);
		return response;
	}
}
