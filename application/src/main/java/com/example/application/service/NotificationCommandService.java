package com.example.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.mapper.NotificationCommandResultMapper;
import com.example.application.port.in.NotificationCommandUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.Outbox;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationCommandService implements NotificationCommandUseCase {

	private final NotificationGroupRepository groupRepository;
	private final OutboxRepository outboxRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final NotificationCommandResultMapper resultMapper;
	private final TransactionTemplate writeTransactionTemplate;
	private final TransactionTemplate readTransactionTemplate;

	public NotificationCommandService(
		NotificationGroupRepository groupRepository,
		OutboxRepository outboxRepository,
		ApplicationEventPublisher eventPublisher,
		NotificationCommandResultMapper resultMapper,
		PlatformTransactionManager transactionManager
	) {
		this.groupRepository = groupRepository;
		this.outboxRepository = outboxRepository;
		this.eventPublisher = eventPublisher;
		this.resultMapper = resultMapper;
		this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
		this.readTransactionTemplate = new TransactionTemplate(transactionManager);
		this.writeTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.readTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.readTransactionTemplate.setReadOnly(true);
	}

	@Override
	public NotificationCommandResult request(SendCommand command) {
		String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());

		Optional<NotificationCommandResult> existing = findExistingResult(command.clientId(), idempotencyKey);
		if (existing.isPresent()) {
			log.info("중복 요청 감지: groupId={}", existing.get().groupId());
			return existing.get();
		}

		try {
			return writeTransactionTemplate.execute(status -> createAndPublish(command, idempotencyKey));
		} catch (DataIntegrityViolationException e) {
			Optional<NotificationCommandResult> recovered = findExistingResult(command.clientId(), idempotencyKey);
			if (recovered.isPresent()) {
				log.info("멱등성 경쟁 감지 후 기존 그룹 반환: groupId={}", recovered.get().groupId());
				return recovered.get();
			}
			throw e;
		}
	}

	private NotificationCommandResult createAndPublish(SendCommand command, String idempotencyKey) {
		NotificationGroup group = createGroup(command, idempotencyKey);
		command.receivers().forEach(group::addNotification);

		NotificationGroup savedGroup = groupRepository.save(group);

		saveOutboxEvents(savedGroup);
		return resultMapper.toResult(savedGroup);
	}

	private Optional<NotificationCommandResult> findExistingResult(String clientId, String idempotencyKey) {
		if (idempotencyKey == null) {
			return Optional.empty();
		}

		Optional<NotificationGroup> existing = readTransactionTemplate.execute(
			status -> groupRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
		);
		return existing == null ? Optional.empty() : existing.map(resultMapper::toResult);
	}

	private void saveOutboxEvents(NotificationGroup savedGroup) {
		List<Long> notificationIds = savedGroup.getNotifications().stream()
			.map(Notification::getId)
			.toList();

		List<Outbox> outboxes = notificationIds.stream()
			.map(Outbox::createNotificationEvent)
			.toList();

		outboxRepository.saveAll(outboxes);

		if (!notificationIds.isEmpty()) {
			eventPublisher.publishEvent(new OutboxSavedEvent(notificationIds));
		}
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

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null) {
			return null;
		}

		String normalized = idempotencyKey.trim();
		return normalized.isBlank() ? null : normalized;
	}
}
