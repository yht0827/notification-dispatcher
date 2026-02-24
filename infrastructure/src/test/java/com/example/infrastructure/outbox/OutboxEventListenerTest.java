package com.example.infrastructure.outbox;

import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.OutboxRepository;
import com.example.infrastructure.stream.outbound.RedisStreamPublisher;

@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

	@Mock
	private RedisStreamPublisher streamPublisher;

	@Mock
	private OutboxRepository outboxRepository;

	private OutboxEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new OutboxEventListener(streamPublisher, outboxRepository);
	}

	@Test
	@DisplayName("커밋 후 Redis Stream에 즉시 발행하고 Outbox를 삭제한다")
	void onOutboxCreated_publishesAndDeletes() {
		// given
		OutboxCreatedEvent event = new OutboxCreatedEvent(List.of(100L, 200L));

		// when
		listener.onOutboxCreated(event);

		// then
		verify(streamPublisher).publish(100L);
		verify(streamPublisher).publish(200L);
		verify(outboxRepository).deleteByAggregateId(100L);
		verify(outboxRepository).deleteByAggregateId(200L);
	}

	@Test
	@DisplayName("발행 실패 시 해당 Outbox는 삭제하지 않는다")
	void onOutboxCreated_doesNotDeleteOnPublishFailure() {
		// given
		OutboxCreatedEvent event = new OutboxCreatedEvent(List.of(100L, 200L));
		doThrow(new RuntimeException("Redis connection failed")).when(streamPublisher).publish(100L);

		// when
		listener.onOutboxCreated(event);

		// then
		verify(streamPublisher).publish(100L);
		verify(streamPublisher).publish(200L);
		verify(outboxRepository, never()).deleteByAggregateId(100L);
		verify(outboxRepository).deleteByAggregateId(200L);
	}

	@Test
	@DisplayName("빈 이벤트는 아무 작업도 하지 않는다")
	void onOutboxCreated_doesNothingForEmptyEvent() {
		// given
		OutboxCreatedEvent event = new OutboxCreatedEvent(List.of());

		// when
		listener.onOutboxCreated(event);

		// then
		verify(streamPublisher, never()).publish(anyLong());
		verify(outboxRepository, never()).deleteByAggregateId(anyLong());
	}
}
