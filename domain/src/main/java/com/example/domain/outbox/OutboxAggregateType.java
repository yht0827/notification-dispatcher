package com.example.domain.outbox;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.example.domain.exception.UnsupportedOutboxTypeException;

public enum OutboxAggregateType {

	NOTIFICATION("Notification");

	private static final String TYPE_NAME = "아웃박스 집계 타입";
	private static final String SUPPORTED_VALUES = Arrays.stream(values())
		.map(OutboxAggregateType::value)
		.collect(Collectors.joining(", "));

	private final String value;

	OutboxAggregateType(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static OutboxAggregateType fromValue(String value) {
		if (value == null || value.isBlank()) {
			throw unsupportedValue(value);
		}

		String candidate = value.trim();
		for (OutboxAggregateType type : values()) {
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
