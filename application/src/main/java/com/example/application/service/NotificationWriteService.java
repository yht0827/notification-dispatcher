package com.example.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationWriteUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.in.result.NotificationGroupReadResult;
import com.example.application.port.in.result.NotificationReadResult;
import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.exception.AccessDeniedException;

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
	private final NotificationUnreadCountCacheRepository unreadCountCacheRepository;

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
			NotificationCommandResult result = notificationWriteExecutor.createAndPublish(command, idempotencyKey);
			evictUnreadCount(command.clientId(), command.receivers());
			return result;
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
	public Optional<NotificationReadResult> markAsRead(String clientId, Long notificationId) {
		LocalDateTime now = LocalDateTime.now();
		var from = NotificationDetailRetentionPolicy.detailFrom(now);
		return notificationRepository.findById(notificationId)
			.filter(
				notification -> NotificationDetailRetentionPolicy.isWithinRetention(notification.getCreatedAt(), from))
			.map(notification -> {
				if (!clientId.equals(notification.getGroup().getClientId())) {
					throw new AccessDeniedException("해당 알림에 대한 접근 권한이 없습니다.");
				}
				notificationReadStatusRepository.markAsRead(notificationId, now);
				evictUnreadCount(clientId, notification.getReceiver());
				LocalDateTime readAt = notificationReadStatusRepository.findReadAtByNotificationId(notificationId);
				return new NotificationReadResult(notificationId, readAt);
			});
	}

	@Override
	@Transactional
	public Optional<NotificationGroupReadResult> markGroupAsRead(String clientId, Long groupId) {
		LocalDateTime now = LocalDateTime.now();
		var from = NotificationDetailRetentionPolicy.detailFrom(now);
		return notificationGroupRepository.findByIdWithNotifications(groupId)
			.filter(group -> NotificationDetailRetentionPolicy.isWithinRetention(group.getCreatedAt(), from))
			.map(group -> {
				if (!clientId.equals(group.getClientId())) {
					throw new AccessDeniedException("해당 알림 그룹에 대한 접근 권한이 없습니다.");
				}
				var notificationIds = group.getNotifications().stream()
					.map(com.example.domain.notification.Notification::getId)
					.toList();
				int readCount = notificationReadStatusRepository.markAllAsRead(notificationIds, now);
				evictUnreadCount(
					clientId,
					group.getNotifications().stream()
						.map(com.example.domain.notification.Notification::getReceiver)
						.distinct()
						.toList()
				);
				return new NotificationGroupReadResult(groupId, readCount, now);
			});
	}

	private void evictUnreadCount(String clientId, List<String> receivers) {
		receivers.stream()
			.filter(receiver -> receiver != null && !receiver.isBlank())
			.distinct()
			.forEach(receiver -> unreadCountCacheRepository.evict(clientId, receiver));
	}

	private void evictUnreadCount(String clientId, String receiver) {
		if (receiver == null || receiver.isBlank()) {
			return;
		}
		unreadCountCacheRepository.evict(clientId, receiver);
	}

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null) {
			return null;
		}

		String normalized = idempotencyKey.trim();
		return normalized.isBlank() ? null : normalized;
	}
}
