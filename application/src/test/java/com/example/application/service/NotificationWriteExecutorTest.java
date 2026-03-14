package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.application.service.mapper.NotificationCommandResultMapper;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.Outbox;

@ExtendWith(MockitoExtension.class)
class NotificationWriteExecutorTest {

	@Mock
	private NotificationGroupRepository groupRepository;

	@Mock
	private OutboxRepository outboxRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private NotificationCommandResultMapper resultMapper;

	private NotificationWriteExecutor executor;

	@BeforeEach
	void setUp() {
		executor = new NotificationWriteExecutor(groupRepository, outboxRepository, eventPublisher, resultMapper);
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
			ReflectionTestUtils.setField(savedGroup.getNotifications().get(0), "id", 101L);
			ReflectionTestUtils.setField(savedGroup.getNotifications().get(1), "id", 102L);
			return savedGroup;
		});
		when(resultMapper.toResult(any(NotificationGroup.class))).thenReturn(new NotificationCommandResult(99L, 2));

		NotificationCommandResult result = executor.createAndPublish(command, "idem-1");

		assertThat(result.groupId()).isEqualTo(99L);
		assertThat(result.totalCount()).isEqualTo(2);
		verify(outboxRepository).saveAll(argThat(matchesOutboxIds(101L, 102L)));
		verify(eventPublisher).publishEvent(new OutboxSavedEvent(List.of(101L, 102L)));
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
		when(resultMapper.toResult(any(NotificationGroup.class))).thenReturn(new NotificationCommandResult(100L, 0));

		NotificationCommandResult result = executor.createAndPublish(command, null);

		assertThat(result.groupId()).isEqualTo(100L);
		assertThat(result.totalCount()).isZero();
		verify(outboxRepository).saveAll(List.of());
		verify(eventPublisher, never()).publishEvent(any());
	}

	private ArgumentMatcher<List<Outbox>> matchesOutboxIds(Long... notificationIds) {
		List<Long> expectedIds = List.of(notificationIds);
		return outboxes -> outboxes != null
			&& outboxes.size() == expectedIds.size()
			&& outboxes.stream().map(Outbox::getAggregateId).toList().equals(expectedIds);
	}
}
