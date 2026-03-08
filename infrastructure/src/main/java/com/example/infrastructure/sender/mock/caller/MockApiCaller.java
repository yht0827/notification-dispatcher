package com.example.infrastructure.sender.mock.caller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.example.application.port.out.result.SendResult;
import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRateLimitException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockApiCaller {
	private final MockApiClient mockApiClient;

	@Retry(name = "mockApi")
	@CircuitBreaker(name = "mockApi", fallbackMethod = "fallbackOnCircuitOpen")
	public SendResult call(MockApiSendRequest request) {
		log.debug("mock API 호출: requestId={}, channel={}", request.requestId(), request.channelType());

		try {
			ResponseEntity<MockApiSendSuccessResponse> response = mockApiClient.send(request);
			if (!response.getStatusCode().is2xxSuccessful()) {
				throw new MockApiRetryableException("외부 API 성공 응답 상태 코드가 아닙니다: " + response.getStatusCode().value());
			}

			MockApiSendSuccessResponse responseBody = response.getBody();

			if (responseBody == null) {
				throw new MockApiRetryableException("외부 API 성공 응답이 비어 있습니다.");
			}

			log.debug("mock API 응답 수신: requestId={}, status={}", request.requestId(), response.getStatusCode().value());
			return SendResult.success();
		} catch (MockApiNonRetryableException | MockApiRetryableException | MockApiRateLimitException e) {
			throw e;
		} catch (RetryableException e) {
			throw new MockApiRetryableException("외부 API 네트워크/타임아웃 오류: " + e.getMessage(), e);
		} catch (RuntimeException e) {
			throw new MockApiRetryableException("외부 API 호출 오류: " + e.getMessage(), e);
		}
	}

	private SendResult fallbackOnCircuitOpen(MockApiSendRequest request, CallNotPermittedException e) {
		log.warn("서킷 브레이커 OPEN: requestId={}, channel={}", request.requestId(), request.channelType());
		return SendResult.failRetryable("서킷 브레이커 OPEN - 외부 API 연속 장애");
	}
}
