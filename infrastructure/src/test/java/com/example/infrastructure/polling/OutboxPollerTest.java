package com.example.infrastructure.polling;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyLong;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.repository.OutboxRepository;
import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxAggregateType;
import com.example.domain.outbox.OutboxEventType;
import com.example.domain.outbox.OutboxStatus;
import com.example.application.port.out.NotificationEventPublisher;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

	private static final Field OUTBOX_ID_FIELD = outboxIdField();
	private static final int BATCH_SIZE = 100;

	@Mock
	private OutboxRepository outboxRepository;

	@Mock
	private NotificationEventPublisher eventPublisher;

	@Mock
	private MeterRegistry meterRegistry;

	@Mock
	private Counter counter;

	private OutboxPoller outboxPoller;

	@BeforeEach
	void setUp() {
		lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
		outboxPoller = new OutboxPoller(outboxRepository, eventPublisher, new OutboxProperties(BATCH_SIZE), meterRegistry);
	}

	@Test
	@DisplayName("PENDING 상태 Outbox가 없으면 아무 작업도 하지 않는다")
	void pollAndPublish_doesNothingWhenNoPendingOutboxes() {
		// given
		when(outboxRepository.findByStatus(OutboxStatus.PENDING, BATCH_SIZE))
			.thenReturn(Collections.emptyList());

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(eventPublisher, never()).publish(anyLong());
		verify(outboxRepository, never()).deleteAll(any());
	}

	@Test
	@DisplayName("PENDING Outbox를 메시징으로 발행하고 삭제한다")
	void pollAndPublish_publishesAndDeletesSuccessfully() {
		// given
		Outbox outbox1 = createOutbox(1L, 100L);
		Outbox outbox2 = createOutbox(2L, 200L);

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, BATCH_SIZE))
			.thenReturn(List.of(outbox1, outbox2));

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(eventPublisher).publish(100L);
		verify(eventPublisher).publish(200L);
		verify(outboxRepository).deleteAll(List.of(outbox1, outbox2));
	}

	@Test
	@DisplayName("메시징 발행 실패한 Outbox는 삭제하지 않는다")
	void pollAndPublish_doesNotDeleteFailedPublishes() {
		// given
		Outbox outbox1 = createOutbox(1L, 100L);
		Outbox outbox2 = createOutbox(2L, 200L);
		Outbox outbox3 = createOutbox(3L, 300L);

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, BATCH_SIZE))
			.thenReturn(List.of(outbox1, outbox2, outbox3));

		// outbox2 발행 실패
		doNothing().when(eventPublisher).publish(100L);
		doThrow(new RuntimeException("messaging publish failed")).when(eventPublisher).publish(200L);
		doNothing().when(eventPublisher).publish(300L);

		// when
		outboxPoller.pollAndPublish();

		// then - outbox1, outbox3만 삭제
		verify(outboxRepository).deleteAll(List.of(outbox1, outbox3));
	}

	@Test
	@DisplayName("모든 발행이 실패하면 deleteAll을 호출하지 않는다")
	void pollAndPublish_doesNotCallDeleteWhenAllFail() {
		// given
		Outbox outbox = createOutbox(1L, 100L);

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, BATCH_SIZE))
			.thenReturn(List.of(outbox));
		doThrow(new RuntimeException("messaging publish failed")).when(eventPublisher).publish(100L);

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(outboxRepository, never()).deleteAll(any());
	}

	@Test
	@DisplayName("발행 성공 시 Outbox 상태가 PROCESSED로 변경된다")
	void pollAndPublish_marksOutboxAsProcessed() {
		// given
		Outbox outbox = spy(createOutbox(1L, 100L));

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, BATCH_SIZE))
			.thenReturn(List.of(outbox));

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(outbox).markAsProcessed();
	}

	@Test
	@DisplayName("발행 실패 시 Outbox 상태를 변경하지 않는다")
	void pollAndPublish_doesNotMarkAsProcessedOnFailure() {
		// given
		Outbox outbox = spy(createOutbox(1L, 100L));

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, BATCH_SIZE))
			.thenReturn(List.of(outbox));
		doThrow(new RuntimeException("messaging publish failed")).when(eventPublisher).publish(100L);

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(outbox, never()).markAsProcessed();
	}

	@Test
	@DisplayName("GROUP outbox는 payload의 notification ids를 모두 발행한 뒤 삭제한다")
	void pollAndPublish_groupOutbox_publishesPayloadNotifications() {
		Outbox groupOutbox = spy(createGroupOutbox(10L, 999L, "100,200,300"));

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, BATCH_SIZE))
			.thenReturn(List.of(groupOutbox));

		outboxPoller.pollAndPublish();

		verify(eventPublisher).publish(100L);
		verify(eventPublisher).publish(200L);
		verify(eventPublisher).publish(300L);
		verify(outboxRepository).deleteAll(List.of(groupOutbox));
		verify(groupOutbox).markAsProcessed();
	}

	private Outbox createOutbox(Long id, Long aggregateId) {
		Outbox outbox = Outbox.create(
			OutboxAggregateType.NOTIFICATION,
			aggregateId,
			OutboxEventType.NOTIFICATION_CREATED,
			"{}"
		);
		setOutboxId(outbox, id);
		return outbox;
	}

	private Outbox createGroupOutbox(Long id, Long aggregateId, String payload) {
		Outbox outbox = Outbox.create(
			OutboxAggregateType.GROUP,
			aggregateId,
			OutboxEventType.NOTIFICATION_CREATED,
			payload
		);
		setOutboxId(outbox, id);
		return outbox;
	}

	private static Field outboxIdField() {
		try {
			Field field = Outbox.class.getDeclaredField("id");
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException("Outbox id 필드를 찾을 수 없습니다.", e);
		}
	}

	private void setOutboxId(Outbox outbox, Long id) {
		try {
			OUTBOX_ID_FIELD.set(outbox, id);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Outbox id 필드 설정에 실패했습니다.", e);
		}
	}
}
