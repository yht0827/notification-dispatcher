package com.example.worker.sender.mock.http;

import org.springframework.stereotype.Component;

import com.example.application.port.out.SendResult;
import com.example.worker.sender.mock.dto.MockApiSendRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class SmsMockApiCaller extends AbstractMockApiCaller {

	public SmsMockApiCaller(MockApiClient mockApiClient, MeterRegistry meterRegistry) {
		super(mockApiClient, meterRegistry);
	}

	@Override
	@CircuitBreaker(name = "sms-api", fallbackMethod = "circuitFallback")
	@Retry(name = "sms-api")
	@RateLimiter(name = "sms-api", fallbackMethod = "rateLimitFallback")
	public SendResult call(MockApiSendRequest request) {
		return doCall(request);
	}
}
