package com.example.worker.sender.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.worker.sender.mock.http.MockApiCaller;
import com.example.worker.sender.mock.config.MockApiProperties;
import com.example.worker.sender.mock.dto.MockApiSendRequest;
import com.example.worker.sender.mock.exception.MockApiNonRetryableException;
import com.example.worker.sender.mock.exception.MockApiRateLimitException;
import com.example.worker.sender.mock.exception.MockApiRetryableException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MockApiSenderTest {

	@Mock
	private MockApiCaller mockApiCaller;

	@Mock
	private MockApiProperties properties;

	private SimpleMeterRegistry meterRegistry;
	private MockApiSender mockApiSender;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		mockApiSender = new MockApiSender(mockApiCaller, properties, meterRegistry);
	}

	@Test
	@DisplayName("mock API가 비활성화이면 성공으로 처리하고 외부 호출하지 않는다")
	void send_returnsSuccessWhenDisabled() {
		when(properties.isEnabled()).thenReturn(false);

		SendResult result = mockApiSender.send(createNotification(), ChannelType.EMAIL);

		assertThat(result.isSuccess()).isTrue();
		verify(mockApiCaller, never()).call(any(MockApiSendRequest.class));
	}

	@Test
	@DisplayName("429 rate limit 예외는 retry delay와 전용 메트릭을 남긴다")
	void send_returnsRetryableFailureWithRetryAfterWhenRateLimited() {
		when(properties.isEnabled()).thenReturn(true);
		when(mockApiCaller.call(any(MockApiSendRequest.class)))
			.thenThrow(new MockApiRateLimitException("rate limit", 15_000L));

		SendResult result = mockApiSender.send(createNotification(), ChannelType.SMS);

		assertThat(result.isFailure()).isTrue();
		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.retryDelayMillis()).isEqualTo(15_000L);
		assertThat(meterRegistry.get("notification.mockapi.failures").tag("type", "rate_limit").counter().count())
			.isEqualTo(1.0d);
	}

	@Test
	@DisplayName("재시도 가능 예외는 retryable 실패로 변환한다")
	void send_returnsRetryableFailureWhenRetryableExceptionThrown() {
		when(properties.isEnabled()).thenReturn(true);
		when(mockApiCaller.call(any(MockApiSendRequest.class)))
			.thenThrow(new MockApiRetryableException("temporary failure"));

		SendResult result = mockApiSender.send(createNotification(), ChannelType.SMS);

		assertThat(result.isFailure()).isTrue();
		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.failReason()).contains("temporary failure");
		assertThat(meterRegistry.get("notification.mockapi.failures").tag("type", "retryable").counter().count())
			.isEqualTo(1.0d);
	}

	@Test
	@DisplayName("재시도 불가 예외는 non-retryable 실패로 변환한다")
	void send_returnsNonRetryableFailureWhenNonRetryableExceptionThrown() {
		when(properties.isEnabled()).thenReturn(true);
		when(mockApiCaller.call(any(MockApiSendRequest.class)))
			.thenThrow(new MockApiNonRetryableException("invalid receiver"));

		SendResult result = mockApiSender.send(createNotification(), ChannelType.KAKAO);

		assertThat(result.isFailure()).isTrue();
		assertThat(result.isNonRetryableFailure()).isTrue();
		assertThat(result.failReason()).contains("invalid receiver");
		assertThat(meterRegistry.get("notification.mockapi.failures").tag("type", "non_retryable").counter().count())
			.isEqualTo(1.0d);
	}

	@Test
	@DisplayName("caller에서 정규화된 retryable 예외는 retryable 실패로 변환한다")
	void send_returnsRetryableFailureForNormalizedCallerException() {
		when(properties.isEnabled()).thenReturn(true);
		when(mockApiCaller.call(any(MockApiSendRequest.class)))
			.thenThrow(new MockApiRetryableException("외부 API 호출 오류: boom"));

		SendResult result = mockApiSender.send(createNotification(), ChannelType.EMAIL);

		assertThat(result.isFailure()).isTrue();
		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.failReason()).contains("외부 API 호출 오류");
	}

	private Notification createNotification() {
		NotificationGroup group = NotificationGroup.create(
			"mock-api-sender-test",
			"group-idem",
			"MyShop",
			"테스트",
			"테스트 내용",
			ChannelType.EMAIL,
			1
		);
		return group.addNotification("user@example.com");
	}
}
