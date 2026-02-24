package com.example.infrastructure.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.OutboxRepository;
import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxAggregateType;
import com.example.domain.outbox.OutboxEventType;
import com.example.domain.outbox.OutboxStatus;
import com.example.infrastructure.stream.outbound.RedisStreamPublisher;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

	@Mock
	private OutboxRepository outboxRepository;

	@Mock
	private RedisStreamPublisher streamPublisher;

	private OutboxPoller outboxPoller;

	@BeforeEach
	void setUp() {
		outboxPoller = new OutboxPoller(outboxRepository, streamPublisher);
	}

	@Test
	@DisplayName("PENDING 상태 Outbox가 없으면 아무 작업도 하지 않는다")
	void pollAndPublish_doesNothingWhenNoPendingOutboxes() {
		// given
		when(outboxRepository.findByStatus(OutboxStatus.PENDING, 100))
			.thenReturn(Collections.emptyList());

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(streamPublisher, never()).publish(anyLong());
		verify(outboxRepository, never()).deleteAll(any());
	}

	@Test
	@DisplayName("PENDING Outbox를 Redis Stream에 발행하고 삭제한다")
	void pollAndPublish_publishesAndDeletesSuccessfully() {
		// given
		Outbox outbox1 = createOutbox(1L, 100L);
		Outbox outbox2 = createOutbox(2L, 200L);

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, 100))
			.thenReturn(List.of(outbox1, outbox2));

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(streamPublisher).publish(100L);
		verify(streamPublisher).publish(200L);
		verify(outboxRepository).deleteAll(List.of(outbox1, outbox2));
	}

	@Test
	@DisplayName("Redis 발행 실패한 Outbox는 삭제하지 않는다")
	void pollAndPublish_doesNotDeleteFailedPublishes() {
		// given
		Outbox outbox1 = createOutbox(1L, 100L);
		Outbox outbox2 = createOutbox(2L, 200L);
		Outbox outbox3 = createOutbox(3L, 300L);

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, 100))
			.thenReturn(List.of(outbox1, outbox2, outbox3));

		// outbox2 발행 실패
		doNothing().when(streamPublisher).publish(100L);
		doThrow(new RuntimeException("Redis connection failed")).when(streamPublisher).publish(200L);
		doNothing().when(streamPublisher).publish(300L);

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

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, 100))
			.thenReturn(List.of(outbox));
		doThrow(new RuntimeException("Redis connection failed")).when(streamPublisher).publish(100L);

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

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, 100))
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

		when(outboxRepository.findByStatus(OutboxStatus.PENDING, 100))
			.thenReturn(List.of(outbox));
		doThrow(new RuntimeException("Redis connection failed")).when(streamPublisher).publish(100L);

		// when
		outboxPoller.pollAndPublish();

		// then
		verify(outbox, never()).markAsProcessed();
	}

	private Outbox createOutbox(Long id, Long aggregateId) {
		Outbox outbox = Outbox.create(
			OutboxAggregateType.NOTIFICATION,
			aggregateId,
			OutboxEventType.NOTIFICATION_CREATED,
			"{}"
		);
		// Reflection으로 ID 설정 (테스트용)
		try {
			var idField = Outbox.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(outbox, id);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return outbox;
	}
}
