package com.example.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.mapper.NotificationResultMapper;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.application.port.in.result.NotificationUnreadCountResult;
import com.example.application.port.out.cache.NotificationDetailCacheRepository;
import com.example.application.port.out.cache.NotificationGroupDetailCacheRepository;
import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.application.port.out.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService implements NotificationQueryUseCase {

	private final NotificationGroupRepository groupRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationReadStatusRepository notificationReadStatusRepository;
	private final NotificationDetailCacheRepository notificationDetailCacheRepository;
	private final NotificationGroupDetailCacheRepository groupDetailCacheRepository;
	private final NotificationUnreadCountCacheRepository unreadCountCacheRepository;
	private final NotificationResultMapper mapper;

	@Override
	public Optional<NotificationGroupResult> getGroup(Long groupId) {
		return groupRepository.findById(groupId).map(mapper::toGroupResult);
	}

	@Override
	public Optional<NotificationGroupDetailResult> getGroupDetail(Long groupId) {
		LocalDateTime from = detailFrom();
		Optional<NotificationGroupDetailResult> cached = groupDetailCacheRepository.get(groupId)
			.filter(detail -> NotificationDetailRetentionPolicy.isWithinRetention(detail.createdAt(), from));
		if (cached.isPresent()) {
			return cached;
		}
		groupDetailCacheRepository.evict(groupId);
		return groupRepository.findByIdWithNotifications(groupId)
			.filter(group -> NotificationDetailRetentionPolicy.isWithinRetention(group.getCreatedAt(), from))
			.map(group -> {
				List<Long> notificationIds = group.getNotifications().stream()
					.map(com.example.domain.notification.Notification::getId)
					.toList();
				Map<Long, LocalDateTime> readAtByNotificationId =
					notificationReadStatusRepository.findReadAtByNotificationIds(notificationIds);
				NotificationGroupDetailResult detail = mapper.toGroupDetailResult(group, readAtByNotificationId);
				groupDetailCacheRepository.put(groupId, detail);
				return detail;
			});
	}

	@Override
	public CursorSlice<NotificationGroupResult> getGroupsByClientId(String clientId, Long cursorId,
		int size) {
		int limit = normalizeSize(size);
		LocalDateTime from = LocalDateTime.now().minusDays(7);
		List<NotificationGroupResult> fetched = groupRepository.findByClientIdWithCursor(clientId, from, cursorId,
				limit + 1)
			.stream()
			.map(mapper::toGroupResult)
			.toList();
		return CursorSlice.of(fetched, limit, NotificationGroupResult::id);
	}

	@Override
	public Optional<NotificationResult> getNotification(Long notificationId) {
		LocalDateTime from = detailFrom();
		Optional<NotificationResult> cached = notificationDetailCacheRepository.get(notificationId)
			.filter(detail -> NotificationDetailRetentionPolicy.isWithinRetention(detail.createdAt(), from));
		if (cached.isPresent()) {
			return cached;
		}
		notificationDetailCacheRepository.evict(notificationId);
		return notificationRepository.findById(notificationId)
			.filter(
				notification -> NotificationDetailRetentionPolicy.isWithinRetention(notification.getCreatedAt(), from))
			.map(notification -> {
				NotificationResult detail = mapper.toNotificationResult(
					notification,
					notificationReadStatusRepository.findReadAtByNotificationId(notification.getId())
				);
				notificationDetailCacheRepository.put(notificationId, detail);
				return detail;
			});
	}

	@Override
	public NotificationUnreadCountResult getUnreadCount(String clientId, String receiver) {
		LocalDateTime from = detailFrom();
		long unreadCount = unreadCountCacheRepository.get(clientId, receiver)
			.orElseGet(() -> {
				long counted = notificationRepository.countUnreadByClientIdAndReceiver(clientId, receiver, from);
				unreadCountCacheRepository.put(clientId, receiver, counted);
				return counted;
			});
		return new NotificationUnreadCountResult(receiver, unreadCount);
	}

	private int normalizeSize(int size) {
		return Math.max(size, 1);
	}

	private LocalDateTime detailFrom() {
		return NotificationDetailRetentionPolicy.detailFrom(LocalDateTime.now());
	}
}
