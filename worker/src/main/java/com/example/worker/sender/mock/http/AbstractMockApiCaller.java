package com.example.worker.sender.mock.http;

import org.springframework.http.ResponseEntity;

import com.example.application.port.out.SendResult;
import com.example.worker.sender.mock.dto.MockApiSendRequest;
import com.example.worker.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.worker.sender.mock.exception.MockApiNonRetryableException;
import com.example.worker.sender.mock.exception.MockApiRateLimitException;
import com.example.worker.sender.mock.exception.MockApiRetryableException;

import com.example.worker.NotificationMetrics;

import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
abstract class AbstractMockApiCaller implements ChannelMockApiCaller {

	protected final MockApiClient mockApiClient;
	protected final MeterRegistry meterRegistry;

	protected SendResult doCall(MockApiSendRequest request) {
		log.debug("mock API 호출: requestId={}, channel={}", request.requestId(), request.channelType());
		try {
			ResponseEntity<MockApiSendSuccessResponse> response = mockApiClient.send(request);
			if (!response.getStatusCode().is2xxSuccessful()) {
				throw new MockApiRetryableException("외부 API 성공 응답 상태 코드가 아닙니다: " + response.getStatusCode().value());
			}
			MockApiSendSuccessResponse body = response.getBody();
			if (body == null) {
				throw new MockApiRetryableException("외부 API 성공 응답이 비어 있습니다.");
			}
			log.debug("mock API 응답 수신: requestId={}, status={}", request.requestId(), response.getStatusCode().value());
			return SendResult.success();
		} catch (RetryableException e) {
			throw new MockApiRetryableException("외부 API 네트워크/타임아웃 오류: " + e.getMessage(), e);
		} catch (MockApiNonRetryableException | MockApiRetryableException | MockApiRateLimitException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new MockApiRetryableException("외부 API 호출 오류: " + e.getMessage(), e);
		}
	}

	// CircuitBreaker fallback
	protected SendResult circuitFallback(MockApiSendRequest request, Throwable t) {
		if (t instanceof CallNotPermittedException) {
			log.warn("서킷 브레이커 OPEN: channel={}, requestId={}", request.channelType(), request.requestId());
			return SendResult.failRetryable("서킷 브레이커 OPEN - " + request.channelType() + " 외부 API 연속 장애");
		}
		if (t instanceof MockApiRateLimitException e) {
			meterRegistry.counter(NotificationMetrics.MOCKAPI_FAILURES, NotificationMetrics.TAG_TYPE, "rate_limit").increment();
			log.info("mock API rate limit: requestId={}, retryAfterMs={}", request.requestId(), e.retryAfterMillis());
			return SendResult.failRetryable(e.getMessage(), e.retryAfterMillis());
		}
		if (t instanceof MockApiNonRetryableException) {
			meterRegistry.counter(NotificationMetrics.MOCKAPI_FAILURES, NotificationMetrics.TAG_TYPE, "non_retryable").increment();
			log.debug("mock API 재시도 불가 실패: requestId={}, reason={}", request.requestId(), t.getMessage());
			return SendResult.failNonRetryable(t.getMessage());
		}
		meterRegistry.counter(NotificationMetrics.MOCKAPI_FAILURES, NotificationMetrics.TAG_TYPE, "retryable").increment();
		log.debug("mock API 재시도 가능 실패: requestId={}, reason={}", request.requestId(), t.getMessage());
		return SendResult.failRetryable(t.getMessage());
	}

	// RateLimiter fallback
	protected SendResult rateLimitFallback(MockApiSendRequest request, RequestNotPermitted e) {
		meterRegistry.counter(NotificationMetrics.RATE_LIMIT_BLOCKED,
			NotificationMetrics.TAG_CHANNEL, request.channelType().name().toLowerCase()).increment();
		log.warn("발신 처리율 초과: channel={}, requestId={}", request.channelType(), request.requestId());
		return SendResult.failRetryable("발신 처리율 초과 - " + request.channelType() + " 채널");
	}
}
