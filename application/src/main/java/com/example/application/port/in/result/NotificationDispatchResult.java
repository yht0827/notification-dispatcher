package com.example.application.port.in.result;

public record NotificationDispatchResult(boolean succeeded, String failReason) {

	public static NotificationDispatchResult success() {
		return new NotificationDispatchResult(true, null);
	}

	public static NotificationDispatchResult fail(String reason) {
		return new NotificationDispatchResult(false, reason);
	}

	public boolean isSuccess() {
		return succeeded;
	}

	public boolean isFailure() {
		return !succeeded;
	}
}
