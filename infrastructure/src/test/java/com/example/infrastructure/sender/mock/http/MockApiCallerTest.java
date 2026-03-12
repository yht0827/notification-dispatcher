package com.example.infrastructure.sender.mock.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.infrastructure.sender.mock.exception.MockApiRateLimitException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MockApiCallerTest {

	@Mock
	private MockApiClient mockApiClient;

	private MockApiCaller mockApiCaller;
	private SimpleMeterRegistry meterRegistry;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(
			CircuitBreakerConfig.custom()
				.slidingWindowSize(10)
				.minimumNumberOfCalls(10)
				.failureRateThreshold(50)
				.build()
		);
		circuitBreakerRegistry.circuitBreaker("email-api");
		circuitBreakerRegistry.circuitBreaker("sms-api");
		circuitBreakerRegistry.circuitBreaker("kakao-api");

		RetryRegistry retryRegistry = RetryRegistry.of(
			RetryConfig.custom()
				.maxAttempts(1)
				.build()
		);
		retryRegistry.retry("email-api");
		retryRegistry.retry("sms-api");
		retryRegistry.retry("kakao-api");

		RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(
			RateLimiterConfig.custom()
				.limitForPeriod(Integer.MAX_VALUE)
				.limitRefreshPeriod(Duration.ofSeconds(1))
				.timeoutDuration(Duration.ZERO)
				.build()
		);
		rateLimiterRegistry.rateLimiter("email-api");
		rateLimiterRegistry.rateLimiter("sms-api");
		rateLimiterRegistry.rateLimiter("kakao-api");

		mockApiCaller = new MockApiCaller(mockApiClient, circuitBreakerRegistry, retryRegistry, rateLimiterRegistry, meterRegistry);
	}

	@Test
	@DisplayName("2xx + body 존재면 result 문자열과 무관하게 성공 처리한다")
	void call_returnsSuccess_whenStatus2xxAndBodyPresent() {
		MockApiSendRequest request = new MockApiSendRequest("req-1", ChannelType.EMAIL, "user@example.com", "hello", null);
		MockApiSendSuccessResponse body = new MockApiSendSuccessResponse("SUCEESS", "req-1", "EMAIL", "2026-03-04T00:00:00Z", 20L);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.ok(body));

		SendResult result = mockApiCaller.call(request);

		assertThat(result.isSuccess()).isTrue();
	}

	@Test
	@DisplayName("2xx라도 body가 없으면 retryable 예외를 던진다")
	void call_throwsRetryable_whenBodyIsNull() {
		MockApiSendRequest request = new MockApiSendRequest("req-2", ChannelType.SMS, "010-0000-0000", "hello", null);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.ok(null));

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("성공 응답이 비어 있습니다");
	}

	@Test
	@DisplayName("비정상 상태 코드가 전달되면 retryable 예외를 던진다")
	void call_throwsRetryable_whenStatusIsNot2xx() {
		MockApiSendRequest request = new MockApiSendRequest("req-3", ChannelType.KAKAO, "kakao-user", "hello", null);
		MockApiSendSuccessResponse body = new MockApiSendSuccessResponse("SUCCESS", "req-3", "KAKAO", "2026-03-04T00:00:00Z", 10L);
		when(mockApiClient.send(request)).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("성공 응답 상태 코드가 아닙니다");
	}

	@Test
	@DisplayName("429 rate limit 예외는 그대로 전파한다")
	void call_propagatesRateLimitException() {
		MockApiSendRequest request = new MockApiSendRequest("req-rate-limit", ChannelType.EMAIL, "user@example.com", "hello", null);
		when(mockApiClient.send(request)).thenThrow(new MockApiRateLimitException("too many requests", 15_000L));

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRateLimitException.class)
			.hasMessageContaining("too many requests");
	}

	@Test
	@DisplayName("타임아웃/네트워크 예외는 retryable 예외로 매핑한다")
	void call_wrapsTimeoutAsRetryable() {
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

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("네트워크/타임아웃 오류");
	}

	@Test
	@DisplayName("예상치 못한 런타임 예외도 retryable 예외로 정규화한다")
	void call_wrapsUnexpectedRuntimeAsRetryable() {
		MockApiSendRequest request = new MockApiSendRequest("req-runtime", ChannelType.EMAIL, "user@example.com", "hello", null);
		when(mockApiClient.send(request)).thenThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> mockApiCaller.call(request))
			.isInstanceOf(MockApiRetryableException.class)
			.hasMessageContaining("외부 API 호출 오류");
	}

	@Test
	@DisplayName("발신 처리율 초과 시 retryable 실패 결과를 반환한다")
	void call_returnsRetryable_whenRateLimitExceeded() {
		RateLimiterRegistry limitedRegistry = RateLimiterRegistry.of(
			RateLimiterConfig.custom()
				.limitForPeriod(1)
				.limitRefreshPeriod(Duration.ofHours(1))
				.timeoutDuration(Duration.ZERO)
				.build()
		);
		limitedRegistry.rateLimiter("email-api");
		CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
		RetryRegistry retryReg = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
		MockApiCaller limitedCaller = new MockApiCaller(mockApiClient, cbRegistry, retryReg, limitedRegistry, meterRegistry);

		MockApiSendRequest first = new MockApiSendRequest("req-rl-0", ChannelType.EMAIL, "a@b.com", "hi", null);
		MockApiSendSuccessResponse body = new MockApiSendSuccessResponse("SUCCESS", "req-rl-0", "EMAIL", "2026-03-04T00:00:00Z", 10L);
		when(mockApiClient.send(first)).thenReturn(ResponseEntity.ok(body));
		limitedCaller.call(first);

		MockApiSendRequest request = new MockApiSendRequest("req-rl-1", ChannelType.EMAIL, "a@b.com", "hi", null);
		SendResult result = limitedCaller.call(request);

		assertThat(result.isSuccess()).isFalse();
		assertThat(meterRegistry.get("notification.outbound.rate_limit_blocked")
			.tag("channel", "email")
			.counter()
			.count()).isEqualTo(1.0d);
	}

	@Test
	@DisplayName("재시도마다 permit을 다시 소비하므로 두 번째 시도에서 local rate limit에 막힐 수 있다")
	void call_consumesPermitPerRetryAttempt() {
		CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
		RetryRegistry retryReg = RetryRegistry.of(
			RetryConfig.custom()
				.maxAttempts(2)
				.retryExceptions(MockApiRetryableException.class)
				.build()
		);
		RateLimiterRegistry limitedRegistry = RateLimiterRegistry.of(
			RateLimiterConfig.custom()
				.limitForPeriod(1)
				.limitRefreshPeriod(Duration.ofHours(1))
				.timeoutDuration(Duration.ZERO)
				.build()
		);
		MockApiCaller limitedCaller = new MockApiCaller(mockApiClient, cbRegistry, retryReg, limitedRegistry, meterRegistry);

		MockApiSendRequest request = new MockApiSendRequest("req-retry-rate-limit", ChannelType.EMAIL, "a@b.com", "hi", null);
		when(mockApiClient.send(request)).thenThrow(new MockApiRetryableException("retryable"));

		SendResult result = limitedCaller.call(request);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.failReason()).contains("발신 처리율 초과");
		verify(mockApiClient, times(1)).send(request);
		assertThat(meterRegistry.get("notification.outbound.rate_limit_blocked")
			.tag("channel", "email")
			.counter()
			.count()).isEqualTo(1.0d);
	}

	@Test
	@DisplayName("재시도 내부 실패가 서킷 브레이커에 누적되어 한 번의 logical call 뒤에도 OPEN 될 수 있다")
	void call_recordsRetryFailuresToCircuitBreaker() {
		CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(
			CircuitBreakerConfig.custom()
				.slidingWindowSize(2)
				.minimumNumberOfCalls(2)
				.failureRateThreshold(50)
				.waitDurationInOpenState(Duration.ofSeconds(30))
				.build()
		);
		RetryRegistry retryReg = RetryRegistry.of(
			RetryConfig.custom()
				.maxAttempts(2)
				.retryExceptions(MockApiRetryableException.class)
				.build()
		);
		RateLimiterRegistry unlimitedRegistry = RateLimiterRegistry.of(
			RateLimiterConfig.custom()
				.limitForPeriod(Integer.MAX_VALUE)
				.limitRefreshPeriod(Duration.ofSeconds(1))
				.timeoutDuration(Duration.ZERO)
				.build()
		);
		MockApiCaller caller = new MockApiCaller(mockApiClient, cbRegistry, retryReg, unlimitedRegistry, meterRegistry);

		MockApiSendRequest request = new MockApiSendRequest("req-cb-retry", ChannelType.EMAIL, "a@b.com", "hi", null);
		when(mockApiClient.send(request)).thenThrow(new MockApiRetryableException("retryable"));

		assertThatThrownBy(() -> caller.call(request))
			.isInstanceOf(MockApiRetryableException.class);
		assertThat(cbRegistry.circuitBreaker("email-api").getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
		verify(mockApiClient, times(2)).send(request);

		SendResult blocked = caller.call(request);
		assertThat(blocked.isSuccess()).isFalse();
		assertThat(blocked.failReason()).contains("서킷 브레이커 OPEN");
	}
}
