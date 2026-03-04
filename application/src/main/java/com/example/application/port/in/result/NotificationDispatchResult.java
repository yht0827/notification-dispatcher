package com.example.application.port.in.result;

public record NotificationDispatchResult(boolean succeeded, String failReason, FailureType failureType) {

	public NotificationDispatchResult {
		if (succeeded) {
			failReason = null;
			failureType = null;
		} else if (failureType == null) {
			failureType = FailureType.RETRYABLE;
		}
	}

	public static NotificationDispatchResult success() {
		return new NotificationDispatchResult(true, null, null);
	}

	public static NotificationDispatchResult fail(String reason) {
		return failRetryable(reason);
	}

	public static NotificationDispatchResult failRetryable(String reason) {
		return new NotificationDispatchResult(false, reason, FailureType.RETRYABLE);
	}

	public static NotificationDispatchResult failNonRetryable(String reason) {
		return new NotificationDispatchResult(false, reason, FailureType.NON_RETRYABLE);
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
