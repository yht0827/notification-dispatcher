package com.example.infrastructure.sender.mock.caller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Response;

class MockApiErrorDecoderTest {

	private final MockApiErrorDecoder decoder = new MockApiErrorDecoder(new ObjectMapper());

	@Test
	@DisplayName("5xx 응답은 retryable 예외로 변환한다")
	void decode_returnsRetryableForServerError() {
		Response response = responseWithStatus(503);

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception)
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("재시도 가능 오류(503)");
	}

	@Test
	@DisplayName("4xx 응답은 non-retryable 예외로 변환한다")
	void decode_returnsNonRetryableForClientError() {
		Response response = responseWithStatus(400);

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception)
			.isInstanceOf(MockApiNonRetryableException.class)
			.hasMessageContaining("재시도 불가 오류(400)");
	}

	@Test
	@DisplayName("실패 응답 body message를 파싱해 예외 메시지에 포함한다")
	void decode_includesParsedMessageFromBody() throws Exception {
		Response response = responseWithBody(500,
			"{\"result\":\"FAIL\",\"requestId\":\"req-1\",\"channelType\":\"EMAIL\",\"errorCode\":\"MOCK_INTERNAL\",\"message\":\"mock backend down\",\"processedAt\":\"2026-03-04T00:00:00Z\",\"latencyMs\":10}");

		Exception exception = decoder.decode("mockApi#send", response);

		assertThat(exception)
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("mock backend down");
	}

	private Response responseWithStatus(int status) {
		Response response = mock(Response.class);
		when(response.status()).thenReturn(status);
		when(response.body()).thenReturn(null);
		return response;
	}

	private Response responseWithBody(int status, String bodyText) throws Exception {
		Response response = mock(Response.class);
		Response.Body body = mock(Response.Body.class);
		when(response.status()).thenReturn(status);
		when(response.body()).thenReturn(body);
		when(body.asInputStream()).thenReturn(new ByteArrayInputStream(bodyText.getBytes(StandardCharsets.UTF_8)));
		return response;
	}
}
