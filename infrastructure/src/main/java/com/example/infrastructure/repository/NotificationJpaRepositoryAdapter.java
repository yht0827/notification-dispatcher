package com.example.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class NotificationJpaRepositoryAdapter {

	private final NotificationJpaRepository jpaRepository;

	public Notification save(Notification notification) {
		return jpaRepository.save(notification);
	}

	public List<Notification> saveAll(List<Notification> notifications) {
		return jpaRepository.saveAll(notifications);
	}

	public Optional<Notification> findById(Long id) {
		return jpaRepository.findById(id);
	}

	public List<Notification> findAllByIdIn(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findAllByIdIn(ids);
	}

	public long countUnreadByClientIdAndReceiver(String clientId, String receiver, LocalDateTime from) {
		return jpaRepository.countUnreadByClientIdAndReceiver(clientId, receiver, from);
	}

	public List<Notification> findByClientIdAndReceiverWithCursor(
		String clientId,
		String receiver,
		LocalDateTime from,
		Long cursorId,
		int limit
	) {
		int normalizedLimit = normalizeLimit(limit);
		return jpaRepository.findByClientIdAndReceiverWithCursor(
			clientId,
			receiver,
			from,
			cursorId,
			PageRequest.of(0, normalizedLimit)
		);
	}

	public List<Notification> findByStatus(NotificationStatus status) {
		return jpaRepository.findByStatus(status);
	}

	public List<Notification> findByStatusAndCreatedAtBefore(
		NotificationStatus status,
		LocalDateTime threshold,
		int limit
	) {
		int normalizedLimit = normalizeLimit(limit);
		return jpaRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
			status,
			threshold,
			PageRequest.of(0, normalizedLimit)
		);
	}

	public void delete(Notification notification) {
		jpaRepository.delete(notification);
	}

	private int normalizeLimit(int limit) {
		return Math.max(limit, 1);
	}
}
