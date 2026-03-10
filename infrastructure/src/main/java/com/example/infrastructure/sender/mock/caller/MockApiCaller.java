package com.example.infrastructure.sender.mock.caller;

import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.example.application.port.out.result.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRateLimitException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockApiCaller {

	private final MockApiClient mockApiClient;
	private final CircuitBreakerRegistry circuitBreakerRegistry;
	private final RetryRegistry retryRegistry;
	private final RateLimiterRegistry rateLimiterRegistry;
	private final MeterRegistry meterRegistry;

	public SendResult call(MockApiSendRequest request) {
		String instanceName = instanceName(request.channelType());
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(instanceName);
		Retry retry = retryRegistry.retry(instanceName);
		RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(instanceName);

		// Retry(outer) → CB → RateLimiter(inner) → actual call
		Supplier<SendResult> callSupplier = () -> doCall(request);
		Supplier<SendResult> withRateLimiter = RateLimiter.decorateSupplier(rateLimiter, callSupplier);
		Supplier<SendResult> withCb = CircuitBreaker.decorateSupplier(cb, withRateLimiter);
		Supplier<SendResult> withRetry = Retry.decorateSupplier(retry, withCb);

		try {
			return withRetry.get();
		} catch (RequestNotPermitted e) {
			meterRegistry.counter("notification.outbound.rate_limit_blocked", "channel",
					request.channelType().name().toLowerCase())
				.increment();
			log.warn("발신 처리율 초과: channel={}, requestId={}", request.channelType(), request.requestId());
			return SendResult.failRetryable("발신 처리율 초과 - " + request.channelType() + " 채널");
		} catch (CallNotPermittedException e) {
			log.warn("서킷 브레이커 OPEN: channel={}, requestId={}", request.channelType(), request.requestId());
			return SendResult.failRetryable("서킷 브레이커 OPEN - " + request.channelType() + " 외부 API 연속 장애");
		}
	}

	private SendResult doCall(MockApiSendRequest request) {
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

	private String instanceName(ChannelType channelType) {
		return channelType.name().toLowerCase() + "-api";
	}
}
