package com.example.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxTest {

	@Test
	@DisplayName("Outbox 생성 시 기본 상태는 PENDING이다")
	void create_initialStateIsPending() {
		Outbox outbox = Outbox.create(
			OutboxAggregateType.NOTIFICATION,
			100L,
			OutboxEventType.NOTIFICATION_CREATED,
			"{\"notificationId\":100}"
		);

		assertThat(outbox.getAggregateType()).isEqualTo(OutboxAggregateType.NOTIFICATION);
		assertThat(outbox.getAggregateId()).isEqualTo(100L);
		assertThat(outbox.getEventType()).isEqualTo(OutboxEventType.NOTIFICATION_CREATED);
		assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(outbox.getProcessedAt()).isNull();
		assertThat(outbox.isPending()).isTrue();
	}

	@Test
	@DisplayName("Notification 전용 Outbox 생성 시 payload는 null이다")
	void createNotificationEvent_payloadIsNull() {
		Outbox outbox = Outbox.createNotificationEvent(200L);

		assertThat(outbox.getAggregateType()).isEqualTo(OutboxAggregateType.NOTIFICATION);
		assertThat(outbox.getAggregateId()).isEqualTo(200L);
		assertThat(outbox.getEventType()).isEqualTo(OutboxEventType.NOTIFICATION_CREATED);
		assertThat(outbox.getPayload()).isNull();
		assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
	}

	@Test
	@DisplayName("처리 완료로 표시하면 상태가 PROCESSED가 되고 processedAt이 설정된다")
	void markAsProcessed_setsStatusAndProcessedAt() {
		Outbox outbox = Outbox.createNotificationEvent(300L);

		outbox.markAsProcessed();

		assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(outbox.getProcessedAt()).isNotNull();
		assertThat(outbox.isPending()).isFalse();
	}
}
