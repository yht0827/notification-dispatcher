package com.example.worker.messaging.inbound;

public record RecordProcessResult(
	long contextId,
	Long notificationId,
	int retryCount,
	Status status,
	String reason,
	Long retryDelayMillis
) {

	public static RecordProcessResult success(long contextId, Long notificationId, int retryCount) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.SUCCESS, null, null);
	}

	public static RecordProcessResult skipped(long contextId, Long notificationId, int retryCount, String reason) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.SKIPPED, reason, null);
	}

	public static RecordProcessResult retryableFailure(long contextId, Long notificationId, int retryCount, String reason) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.RETRYABLE_FAILURE, reason, null);
	}

	public static RecordProcessResult retryableFailure(long contextId, Long notificationId, int retryCount, String reason,
		Long retryDelayMillis) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.RETRYABLE_FAILURE, reason,
			retryDelayMillis);
	}

	public static RecordProcessResult nonRetryableFailure(long contextId, Long notificationId, int retryCount,
		String reason) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.NON_RETRYABLE_FAILURE, reason,
			null);
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
