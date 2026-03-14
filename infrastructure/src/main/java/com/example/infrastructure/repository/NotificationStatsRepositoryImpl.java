package com.example.infrastructure.repository;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.NotificationStatsRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationStatsRepositoryImpl implements NotificationStatsRepository {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public Map<String, Long> countByStatus() {
		return queryStatusCount(
			"SELECT status, COUNT(*) FROM notification GROUP BY status"
		);
	}

	@Override
	public Map<String, Long> countByStatusAndClientId(String clientId) {
		return jdbcTemplate.query(
			"SELECT n.status, COUNT(*) FROM notification n " +
				"JOIN notification_group ng ON n.group_id = ng.id " +
				"WHERE ng.client_id = ? GROUP BY n.status",
			rs -> {
				Map<String, Long> result = new HashMap<>();
				while (rs.next()) {
					result.put(rs.getString(1), rs.getLong(2));
				}
				return result;
			},
			clientId
		);
	}

	private Map<String, Long> queryStatusCount(String sql) {
		return jdbcTemplate.query(sql, rs -> {
			Map<String, Long> result = new HashMap<>();
			while (rs.next()) {
				result.put(rs.getString(1), rs.getLong(2));
			}
			return result;
		});
	}
}
