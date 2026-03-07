package com.example.application.port.in.result;

public record BatchDispatchResult(
	Long notificationId,
	boolean succeeded,
	String failReason,
	NotificationDispatchResult.FailureType failureType
) {

	public BatchDispatchResult {
		if (succeeded) {
			failReason = null;
			failureType = null;
		} else if (failureType == null) {
			failureType = NotificationDispatchResult.FailureType.RETRYABLE;
		}
	}

	public static BatchDispatchResult success(Long notificationId) {
		return new BatchDispatchResult(notificationId, true, null, null);
	}

	public static BatchDispatchResult failRetryable(Long notificationId, String reason) {
		return new BatchDispatchResult(notificationId, false, reason,
			NotificationDispatchResult.FailureType.RETRYABLE);
	}

	public static BatchDispatchResult failNonRetryable(Long notificationId, String reason) {
		return new BatchDispatchResult(notificationId, false, reason,
			NotificationDispatchResult.FailureType.NON_RETRYABLE);
	}

	public boolean isSuccess() {
		return succeeded;
	}

	public boolean isFailure() {
		return !succeeded;
	}

	public boolean isRetryableFailure() {
		return isFailure() && failureType == NotificationDispatchResult.FailureType.RETRYABLE;
	}

	public boolean isNonRetryableFailure() {
		return isFailure() && failureType == NotificationDispatchResult.FailureType.NON_RETRYABLE;
	}
}
