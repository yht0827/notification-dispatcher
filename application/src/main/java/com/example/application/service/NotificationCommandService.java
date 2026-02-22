package com.example.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationCommandUseCase;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.application.port.out.OutboxEventRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.OutboxEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService implements NotificationCommandUseCase {

	private static final String AGGREGATE_TYPE = "Notification";
	private static final String EVENT_TYPE = "NotificationCreated";

	private final NotificationGroupRepository groupRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final NotificationDispatchService dispatchService;

	@Override
	@Transactional
	public NotificationGroup send(SendCommand command) {
		NotificationGroup group = createGroup(command);

		for (String receiver : command.receivers()) {
			String idempotencyKey = UUID.randomUUID().toString();
			group.addNotification(receiver, idempotencyKey);
		}

		NotificationGroup savedGroup = groupRepository.save(group);

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
}
