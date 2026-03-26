package com.example.worker.sender.mock.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.worker.sender.mock.MockApiSender;
import com.example.worker.sender.mock.http.MockApiCaller;
import com.example.worker.sender.mock.http.MockApiClient;
import com.example.worker.sender.mock.http.MockApiErrorDecoder;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableConfigurationProperties(MockApiProperties.class)
@EnableFeignClients(basePackages = "com.example.worker.sender.mock.http")
public class MockApiConfig {

	@Bean
	public MockApiErrorDecoder mockApiErrorDecoder() {
		return new MockApiErrorDecoder();
	}

	@Bean
	public MockApiCaller mockApiCaller(
		MockApiClient mockApiClient,
		CircuitBreakerRegistry circuitBreakerRegistry,
		RetryRegistry retryRegistry,
		RateLimiterRegistry rateLimiterRegistry,
		MeterRegistry meterRegistry
	) {
		return new MockApiCaller(mockApiClient, circuitBreakerRegistry, retryRegistry, rateLimiterRegistry, meterRegistry);
	}

	@Bean
	public MockApiSender mockApiSender(MockApiCaller mockApiCaller, MockApiProperties properties, MeterRegistry meterRegistry) {
		return new MockApiSender(mockApiCaller, properties, meterRegistry);
	}
}
