package com.example.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.NotificationGroupCountUpdate;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationGroupRepositoryImpl implements NotificationGroupRepository {

	private final NotificationGroupJpaRepository jpaRepository;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Override
	public NotificationGroup save(NotificationGroup group) {
		return jpaRepository.save(group);
	}

	@Override
	public NotificationGroup saveAndFlush(NotificationGroup group) {
		return jpaRepository.saveAndFlush(group);
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
	public List<NotificationGroup> findByGroupType(GroupType groupType) {
		return jpaRepository.findByGroupType(groupType);
	}

	@Override
	public void bulkApplyDispatchCounts(List<NotificationGroupCountUpdate> updates) {
		if (updates == null || updates.isEmpty()) {
			return;
		}

		LocalDateTime updatedAt = LocalDateTime.now();
		namedParameterJdbcTemplate.getJdbcTemplate().batchUpdate("""
			UPDATE notification_group
			SET sent_count = sent_count + ?,
			    failed_count = failed_count + ?,
			    updated_at = ?
			WHERE id = ?
			""", new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				NotificationGroupCountUpdate update = updates.get(index);
				ps.setInt(1, update.sentDelta());
				ps.setInt(2, update.failedDelta());
				ps.setObject(3, updatedAt);
				ps.setLong(4, update.groupId());
			}

			@Override
			public int getBatchSize() {
				return updates.size();
			}
		});
	}

	@Override
	public void delete(NotificationGroup group) {
		jpaRepository.delete(group);
	}

	private int normalizeLimit(int limit) {
		return Math.max(limit, 1);
	}
}
