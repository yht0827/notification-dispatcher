package com.example.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationGroupRepositoryImpl implements NotificationGroupRepository {

	private final NotificationGroupJpaRepository jpaRepository;

	@Override
	public NotificationGroup save(NotificationGroup group) {
		return jpaRepository.save(group);
	}

	@Override
	public Optional<NotificationGroup> findById(Long id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<NotificationGroup> findByIdWithNotifications(Long id) {
		return jpaRepository.findByIdWithNotifications(id);
	}

	@Override
	public Optional<NotificationGroup> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey) {
		return jpaRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);
	}

	@Override
	public List<NotificationGroup> findByClientIdWithCursor(String clientId, LocalDateTime from, Long cursorId,
		int limit) {
		int normalizedLimit = normalizeLimit(limit);
		return jpaRepository.findByClientIdWithCursor(clientId, from, cursorId, PageRequest.of(0, normalizedLimit));
	}

	@Override
	public List<NotificationGroup> findRecentByCursor(Long cursorId, int limit) {
		int normalizedLimit = normalizeLimit(limit);
		return jpaRepository.findRecentSlice(cursorId, PageRequest.of(0, normalizedLimit));
	}

	@Override
	public List<NotificationGroup> findByGroupType(GroupType groupType) {
		return jpaRepository.findByGroupType(groupType);
	}

	@Override
	public void delete(NotificationGroup group) {
		jpaRepository.delete(group);
	}

	private int normalizeLimit(int limit) {
		return Math.max(limit, 1);
	}
}
