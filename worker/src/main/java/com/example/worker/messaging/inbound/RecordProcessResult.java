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

	public static RecordProcessResult skippedForConcurrentProcessing(long contextId, Long notificationId,
		int retryCount) {
		return skipped(contextId, notificationId, retryCount, "이미 처리 중인 알림 스킵");
	}

	public static RecordProcessResult skippedForLockConflict(long contextId, Long notificationId, int retryCount) {
		return skipped(contextId, notificationId, retryCount, "낙관적 락 충돌 - 다른 인스턴스가 처리 완료");
	}

	public static RecordProcessResult missingNotificationId(long contextId, int retryCount) {
		return nonRetryableFailure(contextId, null, retryCount, "notificationId 값이 비어 있습니다.");
	}

	public static RecordProcessResult maxRetryExceeded(long contextId, Long notificationId, int retryCount,
		int maxRetryCount, String lastError) {
		String reason = "재시도 한도 도달(" + maxRetryCount + "회) - 마지막 오류: " + normalizeReason(lastError);
		return nonRetryableFailure(contextId, notificationId, retryCount, reason);
	}

	public static RecordProcessResult retryableFailure(long contextId, Long notificationId, int retryCount,
		String reason, Long retryDelayMillis) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.RETRYABLE_FAILURE,
			normalizeReason(reason), retryDelayMillis);
	}

	public static RecordProcessResult nonRetryableFailure(long contextId, Long notificationId, int retryCount,
		String reason) {
		return new RecordProcessResult(contextId, notificationId, retryCount, Status.NON_RETRYABLE_FAILURE,
			normalizeReason(reason), null);
	}

	public static String normalizeReason(String reason) {
		if (reason == null || reason.isBlank()) {
			return "알 수 없는 오류";
		}
		return reason.replaceAll("\\s+", " ").trim();
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
