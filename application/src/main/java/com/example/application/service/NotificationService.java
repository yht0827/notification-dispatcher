package com.example.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationUseCase;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.application.port.out.NotificationRepository;
import com.example.application.port.out.OutboxEventRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.OutboxEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService implements NotificationUseCase {

	private static final String AGGREGATE_TYPE = "Notification";
	private static final String EVENT_TYPE = "NotificationCreated";

	private final NotificationGroupRepository groupRepository;
	private final NotificationRepository notificationRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final NotificationDispatchService dispatchService;

	@Override
	@Transactional
	public NotificationGroup send(SendCommand command) {
		// 1. 그룹 및 알림 생성
		NotificationGroup group = createGroup(command);

		for (String receiver : command.receivers()) {
			String idempotencyKey = UUID.randomUUID().toString();
			group.addNotification(receiver, idempotencyKey);
		}

		NotificationGroup savedGroup = groupRepository.save(group);

		// 2. Outbox 이벤트 저장 + 동기 발송
		for (Notification notification : savedGroup.getNotifications()) {
			saveOutboxEvent(notification);
			dispatchNotification(notification);
		}

		return savedGroup;
	}

	private void saveOutboxEvent(Notification notification) {
		String payload = String.format("{\"notificationId\":%d}", notification.getId());
		OutboxEvent event = OutboxEvent.create(AGGREGATE_TYPE, notification.getId(), EVENT_TYPE, payload);
		outboxEventRepository.save(event);
	}

	private void dispatchNotification(Notification notification) {
		try {
			dispatchService.dispatch(notification);
		} catch (Exception e) {
			log.error("알림 발송 중 예외 발생: id={}, error={}", notification.getId(), e.getMessage());
		}
	}

	private NotificationGroup createGroup(SendCommand command) {
		if (command.receivers().size() == 1) {
			return NotificationGroup.createSingle(
				command.clientId(),
				command.sender(),
				command.title(),
				command.content(),
				command.channelType()
			);
		}
		return NotificationGroup.createBulk(
			command.clientId(),
			command.sender(),
			command.title(),
			command.content(),
			command.channelType()
		);
	}

	@Override
	public Optional<NotificationGroup> getGroup(Long groupId) {
		return groupRepository.findById(groupId);
	}

	@Override
	public List<NotificationGroup> getGroupsByClientId(String clientId) {
		return groupRepository.findByClientId(clientId);
	}

	@Override
	public Optional<Notification> getNotification(Long notificationId) {
		return notificationRepository.findById(notificationId);
	}

	@Override
	public List<Notification> getNotificationsByReceiver(String receiver) {
		return notificationRepository.findByReceiver(receiver);
	}
}
