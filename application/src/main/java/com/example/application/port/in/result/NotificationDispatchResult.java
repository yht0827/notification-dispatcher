package com.example.application.port.in.result;

public record NotificationDispatchResult(
	boolean succeeded,
	String failReason,
	FailureType failureType,
	Long retryDelayMillis
) {

	public NotificationDispatchResult {
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

	public static NotificationDispatchResult success() {
		return new NotificationDispatchResult(true, null, null, null);
	}

	public static NotificationDispatchResult fail(String reason) {
		return failRetryable(reason);
	}

	public static NotificationDispatchResult failRetryable(String reason) {
		return new NotificationDispatchResult(false, reason, FailureType.RETRYABLE, null);
	}

	public static NotificationDispatchResult failRetryable(String reason, Long retryDelayMillis) {
		return new NotificationDispatchResult(false, reason, FailureType.RETRYABLE, retryDelayMillis);
	}

	public static NotificationDispatchResult failNonRetryable(String reason) {
		return new NotificationDispatchResult(false, reason, FailureType.NON_RETRYABLE, null);
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
