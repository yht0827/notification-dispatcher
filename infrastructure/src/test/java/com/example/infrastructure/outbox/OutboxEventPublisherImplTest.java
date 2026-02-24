package com.example.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherImplTest {

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private OutboxEventPublisherImpl publisher;

	@BeforeEach
	void setUp() {
		publisher = new OutboxEventPublisherImpl(eventPublisher);
	}

	@Test
	@DisplayName("빈 목록을 전달하면 이벤트를 발행하지 않는다")
	void publishAfterCommit_doesNothingForEmptyList() {
		// when
		publisher.publishAfterCommit(List.of());

		// then
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("알림 ID 목록을 전달하면 OutboxCreatedEvent를 발행한다")
	void publishAfterCommit_publishesOutboxCreatedEvent() {
		// given
		List<Long> notificationIds = List.of(100L, 200L);

		// when
		publisher.publishAfterCommit(notificationIds);

		// then
		ArgumentCaptor<OutboxCreatedEvent> captor = ArgumentCaptor.forClass(OutboxCreatedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().notificationIds()).isEqualTo(notificationIds);
	}
}
