package com.example.worker;

public final class NotificationMetrics {

	private NotificationMetrics() {
	}

	// ─── 메트릭 이름 ───────────────────────────────────────────────────────────
	public static final String DISPATCH_RESULT        = "notification.dispatch.result";
	public static final String OUTBOX_PUBLISHED       = "notification.outbox.published";
	public static final String OUTBOX_FAILED          = "notification.outbox.failed";
	public static final String OUTBOX_PUBLISH_FAILED  = "notification.outbox.publish_failed";
	public static final String MOCKAPI_FAILURES       = "notification.mockapi.failures";
	public static final String RATE_LIMIT_BLOCKED     = "notification.outbound.rate_limit_blocked";

	// ─── 태그 키 ──────────────────────────────────────────────────────────────
	public static final String TAG_OUTCOME = "outcome";
	public static final String TAG_TYPE    = "type";
	public static final String TAG_CHANNEL = "channel";
}
