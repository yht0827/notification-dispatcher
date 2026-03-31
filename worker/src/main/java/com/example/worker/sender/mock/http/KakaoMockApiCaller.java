package com.example.worker.sender.mock.http;

import org.springframework.stereotype.Component;

import com.example.application.port.out.SendResult;
import com.example.worker.sender.mock.dto.MockApiSendRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class KakaoMockApiCaller extends AbstractMockApiCaller {

	public KakaoMockApiCaller(MockApiClient mockApiClient, MeterRegistry meterRegistry) {
		super(mockApiClient, meterRegistry);
	}

	@Override
	@CircuitBreaker(name = "kakao-api", fallbackMethod = "circuitFallback")
	@Retry(name = "kakao-api")
	@RateLimiter(name = "kakao-api", fallbackMethod = "rateLimitFallback")
	public SendResult call(MockApiSendRequest request) {
		return doCall(request);
	}
}
