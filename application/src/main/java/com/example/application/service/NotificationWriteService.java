package com.example.application.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationWriteUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.in.result.NotificationGroupReadResult;
import com.example.application.port.in.result.NotificationReadResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.application.port.out.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationWriteService implements NotificationWriteUseCase {

	private final NotificationGroupRepository notificationGroupRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationReadStatusRepository notificationReadStatusRepository;
	private final NotificationIdempotencyLookupService idempotencyLookupService;
	private final NotificationWriteExecutor notificationWriteExecutor;

	@Override
	public NotificationCommandResult request(SendCommand command) {
		String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());

		if (idempotencyKey != null) {
			Optional<NotificationCommandResult> existing =
				idempotencyLookupService.findExistingResult(command.clientId(), idempotencyKey);
			if (existing.isPresent()) {
				log.info("중복 요청 감지: groupId={}", existing.get().groupId());
				return existing.get();
			}
		}

		try {
			return notificationWriteExecutor.createAndPublish(command, idempotencyKey);
		} catch (DataIntegrityViolationException e) {
			if (idempotencyKey == null) {
				throw e;
			}
			Optional<NotificationCommandResult> recovered =
				idempotencyLookupService.findExistingResultAfterCollision(command.clientId(), idempotencyKey);
			if (recovered.isPresent()) {
				log.info("멱등성 경쟁 감지 후 기존 그룹 반환: groupId={}", recovered.get().groupId());
				return recovered.get();
			}
			throw e;
		}
	}

	@Override
	@Transactional
	public Optional<NotificationReadResult> markAsRead(Long notificationId) {
		LocalDateTime now = LocalDateTime.now();
		var from = NotificationDetailRetentionPolicy.detailFrom(now);
		return notificationRepository.findById(notificationId)
			.filter(
				notification -> NotificationDetailRetentionPolicy.isWithinRetention(notification.getCreatedAt(), from))
			.map(notification -> {
				notificationReadStatusRepository.markAsRead(notificationId, now);
				LocalDateTime readAt = notificationReadStatusRepository.findReadAtByNotificationId(notificationId);
				return new NotificationReadResult(notificationId, readAt);
			});
	}

	@Override
	@Transactional
	public Optional<NotificationGroupReadResult> markGroupAsRead(Long groupId) {
		LocalDateTime now = LocalDateTime.now();
		var from = NotificationDetailRetentionPolicy.detailFrom(now);
		return notificationGroupRepository.findByIdWithNotifications(groupId)
			.filter(group -> NotificationDetailRetentionPolicy.isWithinRetention(group.getCreatedAt(), from))
			.map(group -> {
				var notificationIds = group.getNotifications().stream()
					.map(com.example.domain.notification.Notification::getId)
					.toList();
				int readCount = notificationReadStatusRepository.markAllAsRead(notificationIds, now);
				return new NotificationGroupReadResult(groupId, readCount, now);
			});
	}

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null) {
			return null;
		}

		String normalized = idempotencyKey.trim();
		return normalized.isBlank() ? null : normalized;
	}
}
