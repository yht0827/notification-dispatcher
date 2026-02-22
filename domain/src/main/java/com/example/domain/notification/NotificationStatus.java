package com.example.domain.notification;

import java.util.Set;

public enum NotificationStatus {

	PENDING(Set.of()),
	SENDING(Set.of()),
	SENT(Set.of()),
	RETRY_WAIT(Set.of()),
	FAILED(Set.of()),
	CANCELED(Set.of());

	private Set<NotificationStatus> allowedTransitions;

	NotificationStatus(Set<NotificationStatus> allowedTransitions) {
		this.allowedTransitions = allowedTransitions;
	}

	static {
		PENDING.allowedTransitions = Set.of(SENDING, CANCELED);
		SENDING.allowedTransitions = Set.of(SENT, RETRY_WAIT, FAILED);
		RETRY_WAIT.allowedTransitions = Set.of(SENDING, FAILED);
	}

	public boolean canTransitionTo(NotificationStatus target) {
		return allowedTransitions.contains(target);
	}

	public boolean isTerminal() {
		return this == SENT || this == FAILED || this == CANCELED;
	}

	public boolean isRetryable() {
		return this == RETRY_WAIT || this == FAILED;
	}
}
