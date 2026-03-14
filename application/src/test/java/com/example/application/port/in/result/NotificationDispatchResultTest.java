package com.example.application.port.in.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDispatchResultTest {

	@Test
	@DisplayName("성공 결과는 전달된 실패 정보가 있어도 모두 무시한다")
	void successConstructor_clearsFailureMetadata() {
		NotificationDispatchResult result = new NotificationDispatchResult(
			true,
			"ignored",
			NotificationDispatchResult.FailureType.NON_RETRYABLE,
			10_000L
		);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.isFailure()).isFalse();
		assertThat(result.failReason()).isNull();
		assertThat(result.failureType()).isNull();
		assertThat(result.retryDelayMillis()).isNull();
		assertThat(result.isRetryableFailure()).isFalse();
		assertThat(result.isNonRetryableFailure()).isFalse();
	}

	@Test
	@DisplayName("실패 결과에 타입이 없으면 RETRYABLE로 기본 설정한다")
	void failureConstructor_defaultsFailureTypeToRetryable() {
		NotificationDispatchResult result = new NotificationDispatchResult(false, "temporary", null, 15_000L);

		assertThat(result.isFailure()).isTrue();
		assertThat(result.failureType()).isEqualTo(NotificationDispatchResult.FailureType.RETRYABLE);
		assertThat(result.retryDelayMillis()).isEqualTo(15_000L);
		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.isNonRetryableFailure()).isFalse();
	}

	@Test
	@DisplayName("재시도 불가 실패 결과는 non-retryable 분기만 true다")
	void failNonRetryable_setsNonRetryableBranch() {
		NotificationDispatchResult result = NotificationDispatchResult.failNonRetryable("invalid");

		assertThat(result.isFailure()).isTrue();
		assertThat(result.isRetryableFailure()).isFalse();
		assertThat(result.isNonRetryableFailure()).isTrue();
	}
}
