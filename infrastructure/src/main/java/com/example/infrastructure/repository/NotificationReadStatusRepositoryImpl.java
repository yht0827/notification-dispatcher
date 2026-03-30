package com.example.infrastructure.repository;

import static java.util.stream.Collectors.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.domain.notification.NotificationReadStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationReadStatusRepositoryImpl implements NotificationReadStatusRepository {

	private final NotificationReadStatusJpaRepository jpaRepository;

	@Override
	public void markAsRead(Long notificationId, LocalDateTime readAt) {
		jpaRepository.insertIgnore(notificationId, readAt);
	}

	@Override
	public int markAllAsRead(List<Long> notificationIds, LocalDateTime readAt) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return 0;
		}

		return notificationIds.stream()
			.distinct()
			.mapToInt(notificationId -> jpaRepository.insertIgnore(notificationId, readAt))
			.sum();
	}

	@Override
	public boolean existsByNotificationId(Long notificationId) {
		return jpaRepository.existsById(notificationId);
	}

	@Override
	public LocalDateTime findReadAtByNotificationId(Long notificationId) {
		return jpaRepository.findByNotificationId(notificationId)
			.map(NotificationReadStatus::getReadAt)
			.orElse(null);
	}

	@Override
	public Set<Long> findReadNotificationIds(List<Long> notificationIds) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return Set.of();
		}
		return jpaRepository.findAllByNotificationIdIn(notificationIds)
			.stream()
			.map(NotificationReadStatus::getNotificationId)
			.collect(toCollection(LinkedHashSet::new));
	}

	@Override
	public Map<Long, LocalDateTime> findReadAtByNotificationIds(List<Long> notificationIds) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return Map.of();
		}
		return jpaRepository.findAllByNotificationIdIn(notificationIds).stream()
			.collect(toMap(NotificationReadStatus::getNotificationId, NotificationReadStatus::getReadAt,
				(left, right) -> left, LinkedHashMap::new));
	}
}
