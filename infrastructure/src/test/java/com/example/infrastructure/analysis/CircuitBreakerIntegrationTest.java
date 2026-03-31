package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.domain.notification.ChannelType;
import com.example.infrastructure.TestApplication;
import com.example.infrastructure.config.MockMessagingConfig;
import com.example.infrastructure.config.TestcontainersConfig;
import com.example.worker.sender.mock.http.EmailMockApiCaller;
import com.example.worker.sender.mock.http.MockApiClient;
import com.example.worker.sender.mock.dto.MockApiSendRequest;
import com.example.worker.sender.mock.dto.MockApiSendSuccessResponse;
import com.example.infrastructure.support.EnabledIfDockerAvailable;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * 서킷 브레이커 상태 전이(CLOSED → OPEN → HALF_OPEN → CLOSED) 검증 통합 테스트.
 *
 * <p>실제 외부 mock 서버를 별도로 띄우는 대신 @MockitoBean으로 MockApiClient(Feign 구현체)를 교체해
 * 서버 추가 없이 동일한 회로 전이를 검증한다.</p>
 *
 * <p>테스트 전용 설정으로 circuit breaker 파라미터를 단축해 실행 시간을 최소화한다:
 * sliding-window=5, min-calls=5, wait-duration=2s, permitted-in-half-open=1</p>
 */
@SpringBootTest(
	classes = TestApplication.class,
	properties = {
		"resilience4j.circuitbreaker.instances.email-api.sliding-window-size=5",
		"resilience4j.circuitbreaker.instances.email-api.minimum-number-of-calls=5",
		"resilience4j.circuitbreaker.instances.email-api.wait-duration-in-open-state=2s",
		"resilience4j.circuitbreaker.instances.email-api.permitted-number-of-calls-in-half-open-state=1",
		"resilience4j.retry.instances.email-api.max-attempts=1"
	}
)
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, MockMessagingConfig.class})
class CircuitBreakerIntegrationTest {

	@MockitoBean
	private MockApiClient mockApiClient;

	@Autowired
	private EmailMockApiCaller mockApiCaller;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	private static final MockApiSendRequest TEST_REQUEST =
		new MockApiSendRequest("cb-test", ChannelType.EMAIL, "test@example.com", "hello", null);

	private static final MockApiSendSuccessResponse SUCCESS_RESPONSE =
		new MockApiSendSuccessResponse("SUCCESS", "cb-test", "EMAIL", "2026-01-01T00:00:00Z", 10L);

	@BeforeEach
	void setUp() {
		circuitBreakerRegistry.circuitBreaker("email-api").reset();
		reset(mockApiClient);
	}

	@Test
	@DisplayName("연속 실패가 minimum-number-of-calls 이상 쌓이면 서킷이 OPEN된다")
	void circuitBreaker_opensAfterConsecutiveFailures() {
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("email-api");
		assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

		when(mockApiClient.send(any())).thenThrow(new RuntimeException("upstream 503"));

		for (int i = 0; i < 5; i++) {
			try {
				mockApiCaller.call(TEST_REQUEST);
			} catch (Exception ignored) {
			}
		}

		assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
	}

	@Test
	@DisplayName("OPEN 상태에서는 Feign 클라이언트에 도달하지 않고 fallback이 즉시 반환된다")
	void circuitBreaker_shortCircuits_whenOpen() {
		when(mockApiClient.send(any())).thenThrow(new RuntimeException("upstream 503"));

		for (int i = 0; i < 5; i++) {
			try {
				mockApiCaller.call(TEST_REQUEST);
			} catch (Exception ignored) {
			}
		}

		clearInvocations(mockApiClient);

		var result = mockApiCaller.call(TEST_REQUEST);

		verify(mockApiClient, never()).send(any());
		assertThat(result.isSuccess()).isFalse();
	}

	@Test
	@DisplayName("OPEN → wait-duration 경과 → 성공 응답 → CLOSED 복구 사이클이 동작한다")
	void circuitBreaker_recovers_afterWaitDuration() throws InterruptedException {
		CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("email-api");

		when(mockApiClient.send(any())).thenThrow(new RuntimeException("upstream 503"));
		for (int i = 0; i < 5; i++) {
			try {
				mockApiCaller.call(TEST_REQUEST);
			} catch (Exception ignored) {
			}
		}
		assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		Thread.sleep(2_500);

		reset(mockApiClient);
		when(mockApiClient.send(any())).thenReturn(ResponseEntity.ok(SUCCESS_RESPONSE));
		var result = mockApiCaller.call(TEST_REQUEST);

		assertThat(result.isSuccess()).isTrue();
		assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}
}
