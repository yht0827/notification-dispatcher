package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.application.service.mapper.NotificationCommandResultMapper;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.event.AdminStatsChangedEvent;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationWriteExecutorTest {

	@Mock
	private NotificationGroupRepository groupRepository;

	@Mock
	private OutboxRepository outboxRepository;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private NotificationCommandResultMapper resultMapper;

	private NotificationWriteExecutor executor;

	@BeforeEach
	void setUp() {
		executor = new NotificationWriteExecutor(
			groupRepository,
			notificationRepository,
			outboxRepository,
			eventPublisher,
			resultMapper
		);
	}

	@Test
	@DisplayName("알림 생성 시 그룹 저장, outbox 저장, 이벤트 발행을 수행한다")
	void createAndPublish_savesGroupOutboxAndPublishesEvent() {
		SendCommand command = new SendCommand(
			"client-a",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			List.of("a@test.com", "b@test.com"),
			"idem-1",
			null
		);

		when(groupRepository.saveAndFlush(any(NotificationGroup.class))).thenAnswer(invocation -> {
			NotificationGroup savedGroup = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedGroup, "id", 99L);
			return savedGroup;
		});
		when(notificationRepository.bulkInsertPending(eq(99L), eq(List.of("a@test.com", "b@test.com")), any()))
			.thenReturn(List.of(101L, 102L));
		when(resultMapper.toResult(any(NotificationGroup.class))).thenReturn(new NotificationCommandResult(99L, 2));

		NotificationCommandResult result = executor.createAndPublish(command, "idem-1");

		assertThat(result.groupId()).isEqualTo(99L);
		assertThat(result.totalCount()).isEqualTo(2);
		verify(outboxRepository).saveGroupNotificationCreatedEvent(eq(99L), eq(List.of(101L, 102L)), isNull(), any());
		verify(eventPublisher).publishEvent(new OutboxSavedEvent(99L, List.of(101L, 102L)));
		verify(eventPublisher).publishEvent(new AdminStatsChangedEvent());
	}

	@Test
	@DisplayName("수신자가 없으면 outbox 이벤트를 발행하지 않는다")
	void createAndPublish_skipsEventWhenNoNotificationExists() {
		SendCommand command = new SendCommand(
			"client-a",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			List.of(),
			null,
			null
		);

		when(groupRepository.saveAndFlush(any(NotificationGroup.class))).thenAnswer(invocation -> {
			NotificationGroup savedGroup = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedGroup, "id", 100L);
			return savedGroup;
		});
		when(notificationRepository.bulkInsertPending(eq(100L), eq(List.of()), any())).thenReturn(List.of());
		when(resultMapper.toResult(any(NotificationGroup.class))).thenReturn(new NotificationCommandResult(100L, 0));

		NotificationCommandResult result = executor.createAndPublish(command, null);

		assertThat(result.groupId()).isEqualTo(100L);
		assertThat(result.totalCount()).isZero();
		verify(outboxRepository).saveGroupNotificationCreatedEvent(eq(100L), eq(List.of()), isNull(), any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("예약 발송은 outbox만 저장하고 즉시 발행 이벤트는 내보내지 않는다")
	void createAndPublish_scheduledRequest_savesOutboxWithoutImmediateEvent() {
		LocalDateTime scheduledAt = LocalDateTime.of(2026, 3, 15, 14, 30);
		SendCommand command = new SendCommand(
			"client-a",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			List.of("a@test.com", "b@test.com", "c@test.com"),
			"idem-scheduled",
			scheduledAt
		);

		when(groupRepository.saveAndFlush(any(NotificationGroup.class))).thenAnswer(invocation -> {
			NotificationGroup savedGroup = invocation.getArgument(0);
			ReflectionTestUtils.setField(savedGroup, "id", 101L);
			return savedGroup;
		});
		when(notificationRepository.bulkInsertPending(eq(101L), eq(List.of("a@test.com", "b@test.com", "c@test.com")), any()))
			.thenReturn(List.of(201L, 202L, 203L));
		when(resultMapper.toResult(any(NotificationGroup.class))).thenReturn(new NotificationCommandResult(101L, 3));

		NotificationCommandResult result = executor.createAndPublish(command, "idem-scheduled");

		assertThat(result.groupId()).isEqualTo(101L);
		assertThat(result.totalCount()).isEqualTo(3);
		verify(outboxRepository).saveGroupNotificationCreatedEvent(eq(101L), eq(List.of(201L, 202L, 203L)),
			eq(scheduledAt), any());
		verify(eventPublisher, never()).publishEvent(any(OutboxSavedEvent.class));
		verify(eventPublisher).publishEvent(new AdminStatsChangedEvent());
	}

}
