package com.example.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxConverterTest {

	private final OutboxAggregateTypeConverter aggregateTypeConverter = new OutboxAggregateTypeConverter();
	private final OutboxEventTypeConverter eventTypeConverter = new OutboxEventTypeConverter();

	@Test
	@DisplayName("AggregateType converter는 null과 value 양쪽을 처리한다")
	void aggregateTypeConverter_handlesNullAndValue() {
		assertThat(aggregateTypeConverter.convertToDatabaseColumn(null)).isNull();
		assertThat(aggregateTypeConverter.convertToDatabaseColumn(OutboxAggregateType.NOTIFICATION))
			.isEqualTo("Notification");
		assertThat(aggregateTypeConverter.convertToEntityAttribute(null)).isNull();
		assertThat(aggregateTypeConverter.convertToEntityAttribute("Notification"))
			.isEqualTo(OutboxAggregateType.NOTIFICATION);
	}

	@Test
	@DisplayName("EventType converter는 null과 value 양쪽을 처리한다")
	void eventTypeConverter_handlesNullAndValue() {
		assertThat(eventTypeConverter.convertToDatabaseColumn(null)).isNull();
		assertThat(eventTypeConverter.convertToDatabaseColumn(OutboxEventType.NOTIFICATION_CREATED))
			.isEqualTo("NotificationCreated");
		assertThat(eventTypeConverter.convertToEntityAttribute(null)).isNull();
		assertThat(eventTypeConverter.convertToEntityAttribute("NotificationCreated"))
			.isEqualTo(OutboxEventType.NOTIFICATION_CREATED);
	}
}
