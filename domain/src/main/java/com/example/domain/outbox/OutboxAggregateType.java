package com.example.domain.outbox;

public enum OutboxAggregateType {

	NOTIFICATION("Notification");

	private final String value;

	OutboxAggregateType(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static OutboxAggregateType fromValue(String value) {
		for (OutboxAggregateType type : values()) {
			if (type.value.equals(value) || type.name().equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unsupported outbox aggregate type: " + value);
	}
}
