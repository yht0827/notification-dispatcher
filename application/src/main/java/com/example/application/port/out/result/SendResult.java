package com.example.application.port.out.result;

public record SendResult(boolean succeeded, String failReason, FailureType failureType) {

	public SendResult {
		if (succeeded) {
			failReason = null;
			failureType = null;
		} else if (failureType == null) {
			failureType = FailureType.RETRYABLE;
		}
	}

	public static SendResult success() {
		return new SendResult(true, null, null);
	}

	public static SendResult fail(String reason) {
		return failRetryable(reason);
	}

	public static SendResult failRetryable(String reason) {
		return new SendResult(false, reason, FailureType.RETRYABLE);
	}

	public static SendResult failNonRetryable(String reason) {
		return new SendResult(false, reason, FailureType.NON_RETRYABLE);
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
