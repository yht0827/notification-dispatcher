package com.example.worker.sender.mock.http;

import org.springframework.stereotype.Component;

import com.example.application.port.out.SendResult;
import com.example.worker.sender.mock.dto.MockApiSendRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class EmailMockApiCaller extends AbstractMockApiCaller {

	public EmailMockApiCaller(MockApiClient mockApiClient, MeterRegistry meterRegistry) {
		super(mockApiClient, meterRegistry);
	}

	@Override
	@CircuitBreaker(name = "email-api", fallbackMethod = "circuitFallback")
	@Retry(name = "email-api")
	@RateLimiter(name = "email-api", fallbackMethod = "rateLimitFallback")
	public SendResult call(MockApiSendRequest request) {
		return doCall(request);
	}
}
