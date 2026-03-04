package com.example.infrastructure.sender.mock.caller;

import org.springframework.stereotype.Component;

import com.example.application.port.out.result.SendResult;
import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockApiCaller {

	private final MockApiFeignClient mockApiFeignClient;

	@Retry(name = "mockApi")
	@CircuitBreaker(name = "mockApi", fallbackMethod = "fallbackOnCircuitOpen")
	public SendResult call(MockApiSendRequest request) {
		log.debug("mock API 호출: requestId={}, channel={}", request.requestId(), request.channelType());

		MockApiSendSuccessResponse response = mockApiFeignClient.send(request);

		if (response == null || !"SUCCESS".equalsIgnoreCase(response.result())) {
			throw new MockApiRetryableException("외부 API 성공 응답이 비어 있습니다.");
		}

		log.debug("mock API 응답 수신: requestId={}, result={}", request.requestId(), response.result());
		return SendResult.success();
	}

	private SendResult fallbackOnCircuitOpen(MockApiSendRequest request, CallNotPermittedException e) {
		log.warn("circuit breaker OPEN: requestId={}, channel={}", request.requestId(), request.channelType());
		return SendResult.fail("circuit breaker OPEN - 외부 API 연속 장애");
	}
}
