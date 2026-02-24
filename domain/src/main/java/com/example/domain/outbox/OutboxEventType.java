package com.example.domain.outbox;

public enum OutboxEventType {

	NOTIFICATION_CREATED("NotificationCreated");

	private final String value;

	OutboxEventType(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static OutboxEventType fromValue(String value) {
		for (OutboxEventType type : values()) {
			if (type.value.equals(value) || type.name().equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unsupported outbox event type: " + value);
	}
}
