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
import com.example.application.port.out.cache.NotificationDetailCacheRepository;
import com.example.application.port.out.cache.NotificationGroupDetailCacheRepository;
import com.example.application.port.out.cache.NotificationGroupListCacheRepository;
import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.exception.AccessDeniedException;
import com.example.domain.notification.Notification;

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
	private final NotificationDetailCacheRepository notificationDetailCacheRepository;
	private final NotificationGroupDetailCacheRepository groupDetailCacheRepository;
	private final NotificationGroupListCacheRepository groupListCacheRepository;
	private final NotificationUnreadCountCacheRepository unreadCountCacheRepository;

	@Override
	public NotificationCommandResult request(SendCommand command) {
		String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());

		Optional<NotificationCommandResult> existing = findExistingResult(command.clientId(), idempotencyKey);
		if (existing.isPresent()) {
			log.info("중복 요청 감지: groupId={}", existing.get().groupId());
			return existing.get();
		}

		try {
			NotificationCommandResult result = notificationWriteExecutor.createAndPublish(command, idempotencyKey);
			evictCachesAfterCreation(command.clientId(), command.receivers());
			return result;
		} catch (DataIntegrityViolationException e) {
			Optional<NotificationCommandResult> recovered = recoverExistingResult(command.clientId(), idempotencyKey);
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
			.filter(notification -> NotificationDetailRetentionPolicy.isWithinRetention(notification.getCreatedAt(), from))
			.map(notification -> {
				validateClientAccess(clientId, notification.getGroup().getClientId(), "해당 알림에 대한 접근 권한이 없습니다.");
				notificationReadStatusRepository.markAsRead(notificationId, now);
				evictCachesAfterRead(clientId, notification);
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
				validateClientAccess(clientId, group.getClientId(), "해당 알림 그룹에 대한 접근 권한이 없습니다.");
				List<Notification> notifications = group.getNotifications();
				List<Long> notificationIds = notificationIds(notifications);
				int readCount = notificationReadStatusRepository.markAllAsRead(notificationIds, now);
				evictCachesAfterGroupRead(clientId, groupId, notificationIds, receivers(notifications));
				return new NotificationGroupReadResult(groupId, readCount, now);
			});
	}

	private Optional<NotificationCommandResult> findExistingResult(String clientId, String idempotencyKey) {
		if (idempotencyKey == null) {
			return Optional.empty();
		}
		return idempotencyLookupService.findExistingResult(clientId, idempotencyKey);
	}

	private Optional<NotificationCommandResult> recoverExistingResult(String clientId, String idempotencyKey) {
		if (idempotencyKey == null) {
			return Optional.empty();
		}
		return idempotencyLookupService.findExistingResultAfterCollision(clientId, idempotencyKey);
	}

	private void evictCachesAfterCreation(String clientId, List<String> receivers) {
		evictGroupList(clientId);
		evictUnreadCount(clientId, receivers);
	}

	private void evictCachesAfterRead(String clientId, Notification notification) {
		evictNotificationDetail(notification.getId());
		evictGroupDetail(notification.getGroup().getId());
		evictUnreadCount(clientId, notification.getReceiver());
	}

	private void evictCachesAfterGroupRead(String clientId, Long groupId, List<Long> notificationIds,
		List<String> receivers) {
		evictNotificationDetails(notificationIds);
		evictGroupDetail(groupId);
		evictUnreadCount(clientId, receivers);
	}

	private List<Long> notificationIds(List<Notification> notifications) {
		return notifications.stream()
			.map(Notification::getId)
			.toList();
	}

	private List<String> receivers(List<Notification> notifications) {
		return notifications.stream()
			.map(Notification::getReceiver)
			.toList();
	}

	private void validateClientAccess(String clientId, String ownerClientId, String message) {
		if (!clientId.equals(ownerClientId)) {
			throw new AccessDeniedException(message);
		}
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

	private void evictGroupDetail(Long groupId) {
		if (groupId == null) {
			return;
		}
		groupDetailCacheRepository.evict(groupId);
	}

	private void evictNotificationDetail(Long notificationId) {
		if (notificationId == null) {
			return;
		}
		notificationDetailCacheRepository.evict(notificationId);
	}

	private void evictNotificationDetails(List<Long> notificationIds) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return;
		}
		notificationIds.stream()
			.filter(java.util.Objects::nonNull)
			.distinct()
			.forEach(notificationDetailCacheRepository::evict);
	}

	private void evictGroupList(String clientId) {
		if (clientId == null || clientId.isBlank()) {
			return;
		}
		groupListCacheRepository.evictLatest(clientId);
	}

	private String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null) {
			return null;
		}

		String normalized = idempotencyKey.trim();
		return normalized.isBlank() ? null : normalized;
	}
}
