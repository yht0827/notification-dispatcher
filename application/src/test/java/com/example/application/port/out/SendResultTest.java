package com.example.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendResultTest {

	@Test
	@DisplayName("성공 결과는 실패 관련 필드를 모두 비운다")
	void success_clearsFailureFields() {
		SendResult result = new SendResult(true, "ignored", SendResult.FailureType.NON_RETRYABLE, 100L);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.failReason()).isNull();
		assertThat(result.failureType()).isNull();
		assertThat(result.retryDelayMillis()).isNull();
	}

	@Test
	@DisplayName("실패 결과에서 failureType이 없으면 retryable로 본다")
	void failure_defaultsFailureTypeToRetryable() {
		SendResult result = new SendResult(false, "temporary", null, 100L);

		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.failureType()).isEqualTo(SendResult.FailureType.RETRYABLE);
		assertThat(result.retryDelayMillis()).isEqualTo(100L);
	}

	@Test
	@DisplayName("retry delay가 0 이하이면 null로 정규화한다")
	void failure_normalizesNonPositiveRetryDelay() {
		SendResult result = SendResult.failRetryable("temporary", 0L);

		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.retryDelayMillis()).isNull();
	}

	@Test
	@DisplayName("non-retryable 실패는 retry delay를 제거한다")
	void nonRetryableFailure_clearsRetryDelay() {
		SendResult result = new SendResult(false, "fatal", SendResult.FailureType.NON_RETRYABLE, 300L);

		assertThat(result.isNonRetryableFailure()).isTrue();
		assertThat(result.retryDelayMillis()).isNull();
	}
}
