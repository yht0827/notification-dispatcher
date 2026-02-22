package com.example.domain.notification;

public enum NotificationStatus {

	PENDING,
	SENDING,
	SENT,
	RETRY_WAIT,
	FAILED,
	CANCELED;

	public boolean canTransitionTo(NotificationStatus target) {
		return switch (this) {
			case PENDING -> target == SENDING || target == CANCELED;
			case SENDING -> target == SENT || target == RETRY_WAIT || target == FAILED;
			case RETRY_WAIT -> target == SENDING || target == FAILED;
			case SENT, FAILED, CANCELED -> false;
		};
	}

	public boolean isTerminal() {
		return this == SENT || this == FAILED || this == CANCELED;
	}

	public boolean isRetryable() {
		return this == RETRY_WAIT || this == FAILED;
	}
}
