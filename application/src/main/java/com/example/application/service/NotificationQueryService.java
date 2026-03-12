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
import com.example.application.port.out.cache.NotificationGroupListCacheRepository;
import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.Notification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService implements NotificationQueryUseCase {

	private final NotificationGroupRepository groupRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationReadStatusRepository notificationReadStatusRepository;
	private final NotificationGroupListCacheRepository groupListCacheRepository;
	private final NotificationUnreadCountCacheRepository unreadCountCacheRepository;
	private final NotificationResultMapper mapper;

	@Override
	public Optional<NotificationGroupResult> getGroup(Long groupId) {
		return groupRepository.findById(groupId).map(mapper::toGroupResult);
	}

	@Override
	public Optional<NotificationGroupDetailResult> getGroupDetail(Long groupId) {
		LocalDateTime from = detailFrom();
		return groupRepository.findByIdWithNotifications(groupId)
			.filter(group -> isWithinRetention(group.getCreatedAt(), from))
			.map(this::toGroupDetail);
	}

	@Override
	public CursorSlice<NotificationGroupResult> getGroupsByClientId(String clientId, Long cursorId,
		int size) {
		int limit = normalizeSize(size);
		LocalDateTime from = detailFrom();
		if (groupListCacheRepository.enabled()) {
			Optional<List<NotificationGroupResult>> cached = getCachedLatestGroups(clientId, from);
			if (cached.isPresent()) {
				List<NotificationGroupResult> cachedGroups = cached.get();
				if (canServeFromCache(cachedGroups, cursorId)) {
					return sliceFromCached(cachedGroups, cursorId, limit);
				}
			}
		}

		if (cursorId == null) {
			List<NotificationGroupResult> latestGroups = fetchGroupsByClient(clientId, from, null,
				groupListCacheRepository.latestLimit());
			if (groupListCacheRepository.enabled()) {
				groupListCacheRepository.putLatest(clientId, latestGroups);
			}
			return sliceFromCached(latestGroups, null, limit);
		}

		List<NotificationGroupResult> fetched = fetchGroupsByClient(clientId, from, cursorId, limit + 1);
		return CursorSlice.of(fetched, limit, NotificationGroupResult::id);
	}

	@Override
	public Optional<NotificationResult> getNotification(Long notificationId) {
		LocalDateTime from = detailFrom();
		return notificationRepository.findById(notificationId)
			.filter(notification -> isWithinRetention(notification.getCreatedAt(), from))
			.map(this::toNotificationDetail);
	}

	@Override
	public NotificationUnreadCountResult getUnreadCount(String clientId, String receiver) {
		LocalDateTime from = detailFrom();
		long unreadCount;
		if (unreadCountCacheRepository.enabled()) {
			unreadCount = unreadCountCacheRepository.get(clientId, receiver)
				.orElseGet(() -> {
					long counted = notificationRepository.countUnreadByClientIdAndReceiver(clientId, receiver, from);
					unreadCountCacheRepository.put(clientId, receiver, counted);
					return counted;
				});
		} else {
			unreadCount = notificationRepository.countUnreadByClientIdAndReceiver(clientId, receiver, from);
		}
		return new NotificationUnreadCountResult(receiver, unreadCount);
	}

	private int normalizeSize(int size) {
		return Math.max(size, 1);
	}

	private LocalDateTime detailFrom() {
		return NotificationDetailRetentionPolicy.detailFrom(LocalDateTime.now());
	}

	private NotificationGroupDetailResult toGroupDetail(com.example.domain.notification.NotificationGroup group) {
		List<Long> notificationIds = group.getNotifications().stream()
			.map(Notification::getId)
			.toList();
		Map<Long, LocalDateTime> readAtByNotificationId =
			notificationReadStatusRepository.findReadAtByNotificationIds(notificationIds);
		return mapper.toGroupDetailResult(group, readAtByNotificationId);
	}

	private Optional<List<NotificationGroupResult>> getCachedLatestGroups(String clientId, LocalDateTime from) {
		return groupListCacheRepository.getLatest(clientId)
			.map(groups -> groups.stream()
				.filter(group -> isWithinRetention(group.createdAt(), from))
				.toList());
	}

	private List<NotificationGroupResult> fetchGroupsByClient(String clientId, LocalDateTime from, Long cursorId,
		int limit) {
		return groupRepository.findByClientIdWithCursor(clientId, from, cursorId, limit)
			.stream()
			.map(mapper::toGroupResult)
			.toList();
	}

	private NotificationResult toNotificationDetail(com.example.domain.notification.Notification notification) {
		return mapper.toNotificationResult(
			notification,
			notificationReadStatusRepository.findReadAtByNotificationId(notification.getId())
		);
	}

	private boolean isWithinRetention(LocalDateTime createdAt, LocalDateTime from) {
		return NotificationDetailRetentionPolicy.isWithinRetention(createdAt, from);
	}

	private boolean canServeFromCache(List<NotificationGroupResult> groups, Long cursorId) {
		if (cursorId == null) {
			return true;
		}
		if (groups.isEmpty()) {
			return true;
		}
		Long minCachedId = groups.getLast().id();
		return cursorId > minCachedId;
	}

	private CursorSlice<NotificationGroupResult> sliceFromCached(List<NotificationGroupResult> groups, Long cursorId,
		int limit) {
		List<NotificationGroupResult> filtered = groups.stream()
			.filter(group -> cursorId == null || group.id() < cursorId)
			.toList();
		return CursorSlice.of(filtered, limit, NotificationGroupResult::id);
	}
}
