package com.example.infrastructure.sender.mock.caller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.application.port.out.result.SendResult;
import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;

@ExtendWith(MockitoExtension.class)
class MockApiCallerTest {

	@Mock
	private MockApiClient mockApiClient;

	@InjectMocks
	private MockApiCaller mockApiCaller;

	@Test
	@DisplayName("2xx + body 존재면 result 문자열과 무관하게 성공 처리한다")
	void call_returnsSuccess_whenStatus2xxAndBodyPresent() {
		MockApiSendRequest request = new MockApiSendRequest("req-1", "EMAIL", "user@example.com", "hello", null);
		MockApiSendSuccessResponse body = new MockApiSendSuccessResponse("SUCEESS", "req-1", "EMAIL", "2026-03-04T00:00:00Z", 20L);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.ok(body));

		SendResult result = mockApiCaller.call(request);

		assertThat(result.isSuccess()).isTrue();
	}

	@Test
	@DisplayName("2xx라도 body가 없으면 retryable 예외를 던진다")
	void call_throwsRetryable_whenBodyIsNull() {
		MockApiSendRequest request = new MockApiSendRequest("req-2", "SMS", "010-0000-0000", "hello", null);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("성공 응답이 비어 있습니다");
	}

	@Test
	@DisplayName("비정상 상태 코드가 전달되면 retryable 예외를 던진다")
	void call_throwsRetryable_whenStatusIsNot2xx() {
		MockApiSendRequest request = new MockApiSendRequest("req-3", "KAKAO", "kakao-user", "hello", null);
		MockApiSendSuccessResponse body = new MockApiSendSuccessResponse("SUCCESS", "req-3", "KAKAO", "2026-03-04T00:00:00Z", 10L);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("성공 응답 상태 코드가 아닙니다");
	}

	@Test
	@DisplayName("타임아웃/네트워크 예외는 retryable 예외로 매핑한다")
	void call_wrapsTimeoutAsRetryable() {
		MockApiSendRequest request = new MockApiSendRequest("req-timeout", "EMAIL", "user@example.com", "hello", null);
		RetryableException timeout = new RetryableException(
			-1,
			"Read timed out",
			Request.HttpMethod.POST,
			new java.net.SocketTimeoutException("Read timed out"),
			0L,
			Request.create(Request.HttpMethod.POST, "/mock/send", Map.of(), null, null, new RequestTemplate())
		);
		when(mockApiClient.send(request)).thenThrow(timeout);

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("네트워크/타임아웃 오류");
	}

	@Test
	@DisplayName("예상치 못한 런타임 예외도 retryable 예외로 정규화한다")
	void call_wrapsUnexpectedRuntimeAsRetryable() {
		MockApiSendRequest request = new MockApiSendRequest("req-runtime", "EMAIL", "user@example.com", "hello", null);
		when(mockApiClient.send(request)).thenThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("외부 API 호출 오류");
	}
}
