package com.example.infrastructure.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.OutboxRepository;
import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxAggregateType;
import com.example.domain.outbox.OutboxEventType;
import com.example.domain.outbox.OutboxStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

	private final OutboxJpaRepository jpaRepository;
	private final JdbcTemplate jdbcTemplate;

	@Override
	public Outbox save(Outbox outbox) {
		return jpaRepository.save(outbox);
	}

	@Override
	public List<Outbox> saveAll(List<Outbox> outboxes) {
		return jpaRepository.saveAll(outboxes);
	}

	@Override
	public void bulkInsertNotificationCreatedEvents(List<Long> notificationIds, LocalDateTime scheduledAt,
		LocalDateTime createdAt) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return;
		}

		jdbcTemplate.batchUpdate("""
			INSERT INTO outbox (
			    aggregate_type,
			    aggregate_id,
			    event_type,
			    payload,
			    status,
			    scheduled_at,
			    processed_at,
			    created_at,
			    updated_at
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""", new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				ps.setString(1, OutboxAggregateType.NOTIFICATION.value());
				ps.setLong(2, notificationIds.get(index));
				ps.setString(3, OutboxEventType.NOTIFICATION_CREATED.value());
				ps.setObject(4, null);
				ps.setString(5, OutboxStatus.PENDING.name());
				ps.setObject(6, scheduledAt);
				ps.setObject(7, null);
				ps.setObject(8, createdAt);
				ps.setObject(9, createdAt);
			}

			@Override
			public int getBatchSize() {
				return notificationIds.size();
			}
		});
	}

	@Override
	public void saveGroupNotificationCreatedEvent(Long groupId, List<Long> notificationIds, LocalDateTime scheduledAt,
		LocalDateTime createdAt) {
		if (groupId == null || notificationIds == null || notificationIds.isEmpty()) {
			return;
		}

		jdbcTemplate.update("""
			INSERT INTO outbox (
			    aggregate_type,
			    aggregate_id,
			    event_type,
			    payload,
			    status,
			    scheduled_at,
			    processed_at,
			    created_at,
			    updated_at
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			OutboxAggregateType.GROUP.value(),
			groupId,
			OutboxEventType.NOTIFICATION_CREATED.value(),
			serializeNotificationIds(notificationIds),
			OutboxStatus.PENDING.name(),
			scheduledAt,
			null,
			createdAt,
			createdAt
		);
	}

	@Override
	public List<Outbox> findByStatus(OutboxStatus status, int limit) {
		int normalizedLimit = normalizeLimit(limit);
		return jpaRepository.findReadyByStatus(status, LocalDateTime.now(), PageRequest.of(0, normalizedLimit));
	}

	@Override
	public void delete(Outbox outbox) {
		jpaRepository.delete(outbox);
	}

	@Override
	public void deleteAll(List<Outbox> outboxes) {
		jpaRepository.deleteAll(outboxes);
	}

	@Override
	public void deleteByAggregateId(Long aggregateId) {
		jpaRepository.deleteByAggregateId(aggregateId);
	}

	@Override
	public void deleteByAggregateIds(List<Long> aggregateIds) {
		if (aggregateIds == null || aggregateIds.isEmpty()) {
			return;
		}
		jpaRepository.deleteByAggregateIdIn(aggregateIds);
	}

	private int normalizeLimit(int limit) {
		return Math.max(limit, 1);
	}

	private String serializeNotificationIds(List<Long> notificationIds) {
		return notificationIds.stream()
			.map(String::valueOf)
			.collect(Collectors.joining(","));
	}
}
