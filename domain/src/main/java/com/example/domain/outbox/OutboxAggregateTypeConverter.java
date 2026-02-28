package com.example.domain.outbox;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OutboxAggregateTypeConverter implements AttributeConverter<OutboxAggregateType, String> {

	@Override
	public String convertToDatabaseColumn(OutboxAggregateType attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public OutboxAggregateType convertToEntityAttribute(String dbData) {
		return dbData == null ? null : OutboxAggregateType.fromValue(dbData);
	}
}
