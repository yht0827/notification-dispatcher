package com.example.domain.outbox;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.example.domain.exception.UnsupportedOutboxTypeException;

public enum OutboxEventType {

	NOTIFICATION_CREATED("NotificationCreated");

	private static final String TYPE_NAME = "아웃박스 이벤트 타입";
	private static final String SUPPORTED_VALUES = Arrays.stream(values())
		.map(OutboxEventType::value)
		.collect(Collectors.joining(", "));

	private final String value;

	OutboxEventType(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static OutboxEventType fromValue(String value) {
		if (value == null || value.isBlank()) {
			throw unsupportedValue(value);
		}

		String candidate = value.trim();
		for (OutboxEventType type : values()) {
			if (type.value.equalsIgnoreCase(candidate) || type.name().equalsIgnoreCase(candidate)) {
				return type;
			}
		}

		throw unsupportedValue(value);
	}

	private static UnsupportedOutboxTypeException unsupportedValue(String value) {
		return new UnsupportedOutboxTypeException(TYPE_NAME, value, SUPPORTED_VALUES);
	}
}
