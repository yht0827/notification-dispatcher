package com.example.infrastructure.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.NotificationFailureUpdate;
import com.example.domain.notification.NotificationStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class NotificationJdbcBulkRepository {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public List<Long> bulkInsertPending(Long groupId, List<String> receivers, LocalDateTime createdAt) {
		if (receivers == null || receivers.isEmpty()) {
			return List.of();
		}

		List<Long> insertedIds = jdbcTemplate.execute((ConnectionCallback<List<Long>>) connection -> {
			try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO notification (
				    group_id,
				    receiver,
				    status,
				    attempt_count,
				    version,
				    created_at,
				    updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
				for (String receiver : receivers) {
					ps.setLong(1, groupId);
					ps.setString(2, receiver);
					ps.setString(3, NotificationStatus.PENDING.name());
					ps.setInt(4, 0);
					ps.setLong(5, 0L);
					ps.setObject(6, createdAt);
					ps.setObject(7, createdAt);
					ps.addBatch();
				}
				ps.executeBatch();

				List<Long> generatedIds = new ArrayList<>(receivers.size());
				try (ResultSet keys = ps.getGeneratedKeys()) {
					while (keys.next()) {
						generatedIds.add(keys.getLong(1));
					}
				}
				return generatedIds;
			}
		});

		if (insertedIds == null || insertedIds.size() != receivers.size()) {
			throw new IllegalStateException("notification bulk insert generated key count mismatch");
		}
		return List.copyOf(insertedIds);
	}

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
}
