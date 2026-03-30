package com.example.infrastructure.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.domain.notification.NotificationStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class NotificationJdbcBulkRepository {

	private final JdbcTemplate jdbcTemplate;

	public List<Long> bulkInsertPending(Long groupId, List<String> receivers, LocalDateTime createdAt) {
		if (receivers == null || receivers.isEmpty()) {
			return List.of();
		}

		List<Long> insertedIds = jdbcTemplate.execute((ConnectionCallback<List<Long>>)connection -> {
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

}
