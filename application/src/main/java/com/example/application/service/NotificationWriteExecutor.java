package com.example.application.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.event.AdminStatsChangedEvent;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.application.port.out.event.SyncDispatchEvent;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.application.service.mapper.NotificationCommandResultMapper;
import com.example.domain.notification.NotificationGroup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationWriteExecutor {

	private final NotificationGroupRepository groupRepository;
	private final NotificationRepository notificationRepository;
	private final OutboxRepository outboxRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final NotificationCommandResultMapper resultMapper;

	@Value("${notification.messaging.enabled:true}")
	private boolean messagingEnabled;

	@Transactional
	public NotificationCommandResult createAndPublish(SendCommand command, String idempotencyKey) {
		NotificationGroup group = createGroup(command, idempotencyKey);
		group.initializeTotalCount(command.receivers().size());

		NotificationGroup savedGroup = groupRepository.saveAndFlush(group);
		LocalDateTime now = LocalDateTime.now();

		// Bulk Insert
		List<Long> notificationIds = notificationRepository.bulkInsertPending(
			savedGroup.getId(),
			command.receivers(),
			now
		);

		if (messagingEnabled) {
			saveOutboxEvents(savedGroup.getId(), notificationIds, command.scheduledAt(), now);
		} else {
			publishSyncDispatch(notificationIds);
		}
		publishAdminStatsChanged(notificationIds);

		return resultMapper.toResult(savedGroup);
	}

	private void saveOutboxEvents(Long groupId, List<Long> notificationIds, LocalDateTime scheduledAt,
		LocalDateTime createdAt) {
		outboxRepository.saveGroupNotificationCreatedEvent(groupId, notificationIds, scheduledAt, createdAt);

		// 즉시 발송만 OutboxSavedEvent 발행 (예약 발송은 OutboxPoller가 처리)
		if (scheduledAt == null && !notificationIds.isEmpty()) {
			eventPublisher.publishEvent(new OutboxSavedEvent(groupId, notificationIds));
		}
		log.debug("Outbox 저장 완료: total={}, scheduled={}", notificationIds.size(), scheduledAt != null);
	}

	private void publishSyncDispatch(List<Long> notificationIds) {
		if (!notificationIds.isEmpty()) {
			eventPublisher.publishEvent(new SyncDispatchEvent(notificationIds));
			log.debug("SyncDispatch 이벤트 발행: count={}", notificationIds.size());
		}
	}

	private void publishAdminStatsChanged(List<Long> notificationIds) {
		if (!notificationIds.isEmpty()) {
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
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
