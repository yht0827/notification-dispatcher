package com.example.application.port.out.result;

public record SendResult(boolean succeeded, String failReason) {

	public static SendResult success() {
		return new SendResult(true, null);
	}

	public static SendResult fail(String reason) {
		return new SendResult(false, reason);
	}

	public boolean isSuccess() {
		return succeeded;
	}

	public boolean isFailure() {
		return !succeeded;
	}
}
