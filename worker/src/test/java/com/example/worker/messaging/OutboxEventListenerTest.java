package com.example.worker.messaging;

import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.repository.OutboxRepository;
import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.worker.messaging.outbound.OutboxEventListener;

@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

	@Mock
	private NotificationEventPublisher eventPublisher;

	@Mock
	private OutboxRepository outboxRepository;

	private OutboxEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new OutboxEventListener(eventPublisher, outboxRepository);
	}

	@Test
	@DisplayName("커밋 후 메시징으로 즉시 발행하고 Outbox를 삭제한다")
	void onOutboxSaved_publishesAndDeletes() {
		// given
		OutboxSavedEvent event = new OutboxSavedEvent(10L, List.of(100L, 200L));

		// when
		listener.onOutboxSaved(event);

		// then
		verify(eventPublisher).publish(100L);
		verify(eventPublisher).publish(200L);
		verify(outboxRepository).deleteByAggregateId(10L);
	}

	@Test
	@DisplayName("발행 실패 시 해당 Outbox는 삭제하지 않는다")
	void onOutboxSaved_doesNotDeleteOnPublishFailure() {
		// given
		OutboxSavedEvent event = new OutboxSavedEvent(10L, List.of(100L, 200L));
		doThrow(new RuntimeException("messaging publish failed")).when(eventPublisher).publish(100L);

		// when
		listener.onOutboxSaved(event);

		// then
		verify(eventPublisher).publish(100L);
		verify(eventPublisher).publish(200L);
		verify(outboxRepository, never()).deleteByAggregateId(10L);
	}

	@Test
	@DisplayName("빈 이벤트는 아무 작업도 하지 않는다")
	void onOutboxSaved_doesNothingForEmptyEvent() {
		// given
		OutboxSavedEvent event = new OutboxSavedEvent(10L, List.of());

		// when
		listener.onOutboxSaved(event);

		// then
		verify(eventPublisher, never()).publish(anyLong());
		verify(outboxRepository, never()).deleteByAggregateId(anyLong());
	}
}
