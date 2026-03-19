package com.example.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.domain.exception.UnsupportedOutboxTypeException;

class OutboxTypeTest {

	@Test
	@DisplayName("OutboxAggregateType은 저장된 value 문자열을 역직렬화한다")
	void aggregateType_fromStoredValue() {
		OutboxAggregateType type = OutboxAggregateType.fromValue("Notification");

		assertThat(type).isEqualTo(OutboxAggregateType.NOTIFICATION);
	}

	@Test
	@DisplayName("OutboxAggregateType은 enum name을 대소문자 무시하고 역직렬화한다")
	void aggregateType_fromEnumNameIgnoreCase() {
		OutboxAggregateType type = OutboxAggregateType.fromValue("notification");

		assertThat(type).isEqualTo(OutboxAggregateType.NOTIFICATION);
	}

	@Test
	@DisplayName("OutboxAggregateType은 잘못된 값에 지원 값 목록을 포함한 메시지를 반환한다")
	void aggregateType_unsupportedValueMessage() {
		assertThatThrownBy(() -> OutboxAggregateType.fromValue("bad-type"))
			.isInstanceOf(UnsupportedOutboxTypeException.class)
			.hasMessageContaining("지원하지 않는 아웃박스 집계 타입입니다")
			.hasMessageContaining("지원 값: Notification, Group");
	}

	@Test
	@DisplayName("OutboxAggregateType은 Group value를 역직렬화한다")
	void aggregateType_fromGroupValue() {
		OutboxAggregateType type = OutboxAggregateType.fromValue("Group");

		assertThat(type).isEqualTo(OutboxAggregateType.GROUP);
	}

	@Test
	@DisplayName("OutboxEventType은 저장된 value 문자열을 역직렬화한다")
	void eventType_fromStoredValue() {
		OutboxEventType type = OutboxEventType.fromValue("NotificationCreated");

		assertThat(type).isEqualTo(OutboxEventType.NOTIFICATION_CREATED);
	}

	@Test
	@DisplayName("OutboxEventType은 enum name을 대소문자 무시하고 역직렬화한다")
	void eventType_fromEnumNameIgnoreCase() {
		OutboxEventType type = OutboxEventType.fromValue("notification_created");

		assertThat(type).isEqualTo(OutboxEventType.NOTIFICATION_CREATED);
	}

	@Test
	@DisplayName("OutboxEventType은 잘못된 값에 지원 값 목록을 포함한 메시지를 반환한다")
	void eventType_unsupportedValueMessage() {
		assertThatThrownBy(() -> OutboxEventType.fromValue("bad-event"))
			.isInstanceOf(UnsupportedOutboxTypeException.class)
			.hasMessageContaining("지원하지 않는 아웃박스 이벤트 타입입니다")
			.hasMessageContaining("지원 값: NotificationCreated");
	}
}
