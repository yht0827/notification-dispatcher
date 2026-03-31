package com.example.worker.sender.mock.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.worker.sender.mock.dto.MockApiSendRequest;
import com.example.worker.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.worker.sender.mock.exception.MockApiRateLimitException;
import com.example.worker.sender.mock.exception.MockApiRetryableException;

import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MockApiCallerTest {

	@Mock
	private MockApiClient mockApiClient;

	private TestMockApiCaller testCaller;
	private SimpleMeterRegistry meterRegistry;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		testCaller = new TestMockApiCaller(mockApiClient, meterRegistry);
	}

	@Test
	@DisplayName("2xx + body 존재면 result 문자열과 무관하게 성공 처리한다")
	void doCall_returnsSuccess_whenStatus2xxAndBodyPresent() {
		MockApiSendRequest request = new MockApiSendRequest("req-1", ChannelType.EMAIL, "user@example.com", "hello", null);
		MockApiSendSuccessResponse body = new MockApiSendSuccessResponse("SUCEESS", "req-1", "EMAIL", "2026-03-04T00:00:00Z", 20L);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.ok(body));

		SendResult result = testCaller.call(request);

		assertThat(result.isSuccess()).isTrue();
	}

	@Test
	@DisplayName("2xx라도 body가 없으면 retryable 예외를 던진다")
	void doCall_throwsRetryable_whenBodyIsNull() {
		MockApiSendRequest request = new MockApiSendRequest("req-2", ChannelType.SMS, "010-0000-0000", "hello", null);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> testCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("성공 응답이 비어 있습니다");
	}

	@Test
	@DisplayName("비정상 상태 코드가 전달되면 retryable 예외를 던진다")
	void doCall_throwsRetryable_whenStatusIsNot2xx() {
		MockApiSendRequest request = new MockApiSendRequest("req-3", ChannelType.KAKAO, "kakao-user", "hello", null);
		MockApiSendSuccessResponse body = new MockApiSendSuccessResponse("SUCCESS", "req-3", "KAKAO", "2026-03-04T00:00:00Z", 10L);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));

		assertThatThrownBy(() -> testCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("성공 응답 상태 코드가 아닙니다");
	}

	@Test
	@DisplayName("429 rate limit 예외는 그대로 전파한다")
	void doCall_propagatesRateLimitException() {
		MockApiSendRequest request = new MockApiSendRequest("req-rate-limit", ChannelType.EMAIL, "user@example.com", "hello", null);
		when(mockApiClient.send(request)).thenThrow(new MockApiRateLimitException("too many requests", 15_000L));

		assertThatThrownBy(() -> testCaller.call(request))
			.isInstanceOf(MockApiRateLimitException.class)
			.hasMessageContaining("too many requests");
	}

	@Test
	@DisplayName("타임아웃/네트워크 예외는 retryable 예외로 매핑한다")
	void doCall_wrapsTimeoutAsRetryable() {
		MockApiSendRequest request = new MockApiSendRequest("req-timeout", ChannelType.EMAIL, "user@example.com", "hello", null);
		RetryableException timeout = new RetryableException(
			-1,
			"Read timed out",
			Request.HttpMethod.POST,
			new java.net.SocketTimeoutException("Read timed out"),
			0L,
			Request.create(Request.HttpMethod.POST, "/mock/send", Map.of(), null, null, new RequestTemplate())
		);
		when(mockApiClient.send(request)).thenThrow(timeout);

		assertThatThrownBy(() -> testCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("네트워크/타임아웃 오류");
	}

	@Test
	@DisplayName("예상치 못한 런타임 예외도 retryable 예외로 정규화한다")
	void doCall_wrapsUnexpectedRuntimeAsRetryable() {
		MockApiSendRequest request = new MockApiSendRequest("req-runtime", ChannelType.EMAIL, "user@example.com", "hello", null);
		when(mockApiClient.send(request)).thenThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> testCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("외부 API 호출 오류");
	}

	static class TestMockApiCaller extends AbstractMockApiCaller {
		TestMockApiCaller(MockApiClient mockApiClient, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
			super(mockApiClient, meterRegistry);
		}

		@Override
		public SendResult call(MockApiSendRequest request) {
			return doCall(request);
		}
	}
}
