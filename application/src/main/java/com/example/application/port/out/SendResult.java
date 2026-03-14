package com.example.application.port.out;

public record SendResult(boolean succeeded, String failReason, FailureType failureType, Long retryDelayMillis) {

	public SendResult {
		if (succeeded) {
			failReason = null;
			failureType = null;
			retryDelayMillis = null;
		} else if (failureType == null) {
			failureType = FailureType.RETRYABLE;
		}
		if (retryDelayMillis != null && retryDelayMillis <= 0) {
			retryDelayMillis = null;
		}
		if (failureType == FailureType.NON_RETRYABLE) {
			retryDelayMillis = null;
		}
	}

	public static SendResult success() {
		return new SendResult(true, null, null, null);
	}

	public static SendResult fail(String reason) {
		return failRetryable(reason);
	}

	public static SendResult failRetryable(String reason) {
		return new SendResult(false, reason, FailureType.RETRYABLE, null);
	}

	public static SendResult failRetryable(String reason, Long retryDelayMillis) {
		return new SendResult(false, reason, FailureType.RETRYABLE, retryDelayMillis);
	}

	public static SendResult failNonRetryable(String reason) {
		return new SendResult(false, reason, FailureType.NON_RETRYABLE, null);
	}

	public boolean isSuccess() {
		return succeeded;
	}

	public boolean isFailure() {
		return !succeeded;
	}

	public boolean isRetryableFailure() {
		return isFailure() && failureType == FailureType.RETRYABLE;
	}

	public boolean isNonRetryableFailure() {
		return isFailure() && failureType == FailureType.NON_RETRYABLE;
	}

	public enum FailureType {
		RETRYABLE,
		NON_RETRYABLE
	}
}
