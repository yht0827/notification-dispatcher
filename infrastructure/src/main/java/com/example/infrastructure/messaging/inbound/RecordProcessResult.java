package com.example.infrastructure.messaging.inbound;

public record RecordProcessResult(
	long contextId,
	Long notificationId,
	int retryCount,
	Status status,
	String reason
) {

	public static RecordProcessResult success(long contextId, Long notificationId, int retryCount) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.SUCCESS, null);
	}

	public static RecordProcessResult skipped(long contextId, Long notificationId, int retryCount, String reason) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.SKIPPED, reason);
	}

	public static RecordProcessResult retryableFailure(long contextId, Long notificationId, int retryCount, String reason) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.RETRYABLE_FAILURE, reason);
	}

	public static RecordProcessResult nonRetryableFailure(long contextId, Long notificationId, int retryCount,
		String reason) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.NON_RETRYABLE_FAILURE, reason);
	}

	public boolean isSuccess() {
		return status == Status.SUCCESS;
	}

	public boolean isSkipped() {
		return status == Status.SKIPPED;
	}

	public boolean isRetryableFailure() {
		return status == Status.RETRYABLE_FAILURE;
	}

	public boolean isNonRetryableFailure() {
		return status == Status.NON_RETRYABLE_FAILURE;
	}

	public enum Status {
		SUCCESS,
		SKIPPED,
		RETRYABLE_FAILURE,
		NON_RETRYABLE_FAILURE
	}
}
