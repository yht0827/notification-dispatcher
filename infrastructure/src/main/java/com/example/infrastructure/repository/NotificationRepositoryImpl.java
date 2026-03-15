package com.example.infrastructure.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.NotificationFailureUpdate;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

	private final NotificationJpaRepository jpaRepository;
	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Override
	public Notification save(Notification notification) {
		return jpaRepository.save(notification);
	}

	@Override
	public List<Notification> saveAll(List<Notification> notifications) {
		return jpaRepository.saveAll(notifications);
	}

	@Override
	public Optional<Notification> findById(Long id) {
		return jpaRepository.findById(id);
	}

	@Override
	public List<Notification> findAllByIdIn(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return jpaRepository.findAllByIdIn(ids);
	}

	@Override
	public long countUnreadByClientIdAndReceiver(String clientId, String receiver, LocalDateTime from) {
		return jpaRepository.countUnreadByClientIdAndReceiver(clientId, receiver, from);
	}

	@Override
	public List<Notification> findByClientIdAndReceiverWithCursor(String clientId, String receiver, LocalDateTime from,
		Long cursorId, int limit) {
		int normalizedLimit = normalizeLimit(limit);
		return jpaRepository.findByClientIdAndReceiverWithCursor(clientId, receiver, from, cursorId,
			PageRequest.of(0, normalizedLimit));
	}

	@Override
	public List<Notification> findByStatus(NotificationStatus status) {
		return jpaRepository.findByStatus(status);
	}

	@Override
	public List<Notification> findByStatusAndCreatedAtBefore(NotificationStatus status, LocalDateTime threshold,
		int limit) {
		int normalizedLimit = normalizeLimit(limit);
		return jpaRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
			status,
			threshold,
			PageRequest.of(0, normalizedLimit)
		);
	}

	@Override
	public void bulkStartSending(List<Long> notificationIds, LocalDateTime updatedAt) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return;
		}

		namedParameterJdbcTemplate.update("""
				UPDATE notification
				SET status = :sendingStatus,
				    attempt_count = attempt_count + 1,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id IN (:ids)
				  AND status IN (:allowedStatuses)
				""",
			new MapSqlParameterSource()
				.addValue("sendingStatus", NotificationStatus.SENDING.name())
				.addValue("updatedAt", updatedAt)
				.addValue("ids", notificationIds)
				.addValue("allowedStatuses", List.of(
					NotificationStatus.PENDING.name(),
					NotificationStatus.SENDING.name()
				))
		);
	}

	@Override
	public void bulkMarkAsSent(List<Long> notificationIds, LocalDateTime sentAt, LocalDateTime updatedAt) {
		if (notificationIds == null || notificationIds.isEmpty()) {
			return;
		}

		namedParameterJdbcTemplate.update("""
				UPDATE notification
				SET status = :sentStatus,
				    sent_at = :sentAt,
				    fail_reason = NULL,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id IN (:ids)
				  AND status = :currentStatus
				""",
			new MapSqlParameterSource()
				.addValue("sentStatus", NotificationStatus.SENT.name())
				.addValue("sentAt", sentAt)
				.addValue("updatedAt", updatedAt)
				.addValue("ids", notificationIds)
				.addValue("currentStatus", NotificationStatus.SENDING.name())
		);
	}

	@Override
	public void bulkMarkAsFailed(List<NotificationFailureUpdate> failureUpdates, LocalDateTime updatedAt) {
		if (failureUpdates == null || failureUpdates.isEmpty()) {
			return;
		}

		jdbcTemplate.batchUpdate("""
			UPDATE notification
			SET status = ?,
			    fail_reason = ?,
			    updated_at = ?,
			    version = version + 1
			WHERE id = ?
			  AND status = ?
			""", new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int index) throws SQLException {
				NotificationFailureUpdate update = failureUpdates.get(index);
				ps.setString(1, NotificationStatus.FAILED.name());
				ps.setString(2, update.failReason());
				ps.setObject(3, updatedAt);
				ps.setLong(4, update.notificationId());
				ps.setString(5, NotificationStatus.SENDING.name());
			}

			@Override
			public int getBatchSize() {
				return failureUpdates.size();
			}
		});
	}

	@Override
	public void delete(Notification notification) {
		jpaRepository.delete(notification);
	}

	private int normalizeLimit(int limit) {
		return Math.max(limit, 1);
	}
}
