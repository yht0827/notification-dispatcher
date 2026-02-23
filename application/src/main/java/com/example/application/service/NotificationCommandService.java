package com.example.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationCommandUseCase;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.application.port.out.OutboxRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService implements NotificationCommandUseCase {

	private final NotificationGroupRepository groupRepository;
	private final OutboxRepository outboxRepository;

	@Override
	@Transactional
	public NotificationGroup request(SendCommand command) {
		String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
		if (idempotencyKey != null) {
			Optional<NotificationGroup> existing = groupRepository.findByClientIdAndIdempotencyKey(
				command.clientId(), idempotencyKey);
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
		saveOutboxEvents(savedGroup);
		return savedGroup;
	}

	private void saveOutboxEvents(NotificationGroup savedGroup) {
		List<Outbox> outboxes = savedGroup.getNotifications().stream()
			.map(Notification::getId)
			.map(Outbox::createNotificationEvent)
			.toList();
		outboxRepository.saveAll(outboxes);
		log.debug("Outbox 저장 완료: count={}", outboxes.size());
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
