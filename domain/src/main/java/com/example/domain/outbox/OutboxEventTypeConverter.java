package com.example.domain.outbox;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OutboxEventTypeConverter implements AttributeConverter<OutboxEventType, String> {

	@Override
	public String convertToDatabaseColumn(OutboxEventType attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public OutboxEventType convertToEntityAttribute(String dbData) {
		return dbData == null ? null : OutboxEventType.fromValue(dbData);
	}
}
