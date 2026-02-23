package com.example.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationCommandUseCase;
import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService implements NotificationCommandUseCase {

	private final NotificationGroupRepository groupRepository;
	private final NotificationEventPublisher eventPublisher;

	@Override
	@Transactional
	public NotificationGroup request(SendCommand command) {
		String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
		if (idempotencyKey != null) {
			Optional<NotificationGroup> existing = groupRepository.findByClientIdAndIdempotencyKey(command.clientId(),
				idempotencyKey);
			if (existing.isPresent()) {
				log.info("중복 요청 감지: groupId={}", existing.get().getId());
				return existing.get();
			}
		}
		return createAndPublish(command, idempotencyKey);
	}

	private NotificationGroup createAndPublish(SendCommand command, String idempotencyKey) {
		NotificationGroup group = createGroup(command, idempotencyKey);
		command.receivers().forEach(group::addNotification);
		NotificationGroup savedGroup = groupRepository.save(group);
		savedGroup.getNotifications().forEach(this::publishEvent);
		return savedGroup;
	}

	private void publishEvent(Notification notification) {
		try {
			eventPublisher.publish(notification.getId());
		} catch (Exception e) {
			log.error("Redis Stream 발행 실패: id={}, error={}", notification.getId(), e.getMessage());
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

	private String normalizeIdempotencyKey(String raw) {
		return (raw == null || raw.isBlank()) ? null : raw.trim();
	}
}
