package com.example.application.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.service.mapper.NotificationCommandResultMapper;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.application.port.out.event.SyncDispatchEvent;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationWriteExecutor {

	private final NotificationGroupRepository groupRepository;
	private final OutboxRepository outboxRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final NotificationCommandResultMapper resultMapper;

	@Value("${notification.messaging.enabled:true}")
	private boolean messagingEnabled;

	@Transactional
	public NotificationCommandResult createAndPublish(SendCommand command, String idempotencyKey) {
		NotificationGroup group = createGroup(command, idempotencyKey);
		command.receivers().forEach(group::addNotification);

		NotificationGroup savedGroup = groupRepository.saveAndFlush(group);

		if (messagingEnabled) {
			saveOutboxEvents(savedGroup, command.scheduledAt());
		} else {
			publishSyncDispatch(savedGroup);
		}

		return resultMapper.toResult(savedGroup);
	}

	private void saveOutboxEvents(NotificationGroup savedGroup, LocalDateTime scheduledAt) {
		List<Long> notificationIds = savedGroup.getNotifications().stream()
			.map(Notification::getId)
			.toList();

		List<Outbox> outboxes = notificationIds.stream()
			.map(id -> Outbox.createNotificationEvent(id, scheduledAt))
			.toList();

		outboxRepository.saveAll(outboxes);

		// 즉시 발송만 OutboxSavedEvent 발행 (예약 발송은 OutboxPoller가 처리)
		if (scheduledAt == null && !notificationIds.isEmpty()) {
			eventPublisher.publishEvent(new OutboxSavedEvent(notificationIds));
		}
		log.debug("Outbox 저장 완료: total={}, scheduled={}", outboxes.size(), scheduledAt != null);
	}

	private void publishSyncDispatch(NotificationGroup savedGroup) {
		List<Long> notificationIds = savedGroup.getNotifications().stream()
			.map(Notification::getId)
			.toList();
		if (!notificationIds.isEmpty()) {
			eventPublisher.publishEvent(new SyncDispatchEvent(notificationIds));
			log.debug("SyncDispatch 이벤트 발행: count={}", notificationIds.size());
		}
	}

	private NotificationGroup createGroup(SendCommand command, String idempotencyKey) {
		return NotificationGroup.create(
			command.clientId(),
			idempotencyKey,
			command.sender(),
			command.title(),
			command.content(),
			command.channelType(),
			command.receivers().size()
		);
	}
}
