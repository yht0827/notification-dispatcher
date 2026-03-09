package com.example.infrastructure.polling;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.out.cache.NotificationGroupDetailCacheRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class NotificationArchiveService {

	private static final List<String> TERMINAL_STATUSES = List.of("SENT", "FAILED", "CANCELED");

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	private final ArchiveProperties archiveProperties;
	private final TransactionTemplate transactionTemplate;
	private final NotificationGroupDetailCacheRepository groupDetailCacheRepository;

	public ArchiveRunResult archiveExpiredData() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(archiveProperties.resolveRetentionDays());

		int archivedNotifications = 0;
		while (true) {
			Integer batchArchived = transactionTemplate.execute(status -> archiveNotificationBatch(cutoff));
			int count = batchArchived == null ? 0 : batchArchived;
			if (count == 0) {
				break;
			}
			archivedNotifications += count;
		}

		int archivedGroups = 0;
		while (true) {
			Integer batchArchived = transactionTemplate.execute(status -> archiveCompletedGroupBatch(cutoff));
			int count = batchArchived == null ? 0 : batchArchived;
			if (count == 0) {
				break;
			}
			archivedGroups += count;
		}

		ArchiveRunResult result = new ArchiveRunResult(cutoff, archivedNotifications, archivedGroups);
		if (result.hasWork()) {
			log.info("Archive 완료: cutoff={}, notifications={}, groups={}",
				result.cutoff(), result.archivedNotifications(), result.archivedGroups());
		}
		return result;
	}

	public void ensureNextMonthPartitions() {
		YearMonth nextMonth = YearMonth.now().plusMonths(1);
		ensureNextMonthPartition("notification_archive", nextMonth);
		ensureNextMonthPartition("notification_group_archive", nextMonth);
		ensureNextMonthPartition("notification_read_status_archive", nextMonth);
	}

	private int archiveNotificationBatch(LocalDateTime cutoff) {
		List<Long> ids = findArchivableNotificationIds(cutoff, archiveProperties.resolveBatchSize());
		if (ids.isEmpty()) {
			return 0;
		}

		LocalDateTime archivedAt = LocalDateTime.now();
		insertNotificationReadStatusArchive(ids, archivedAt);
		deleteNotificationReadStatuses(ids);
		insertNotificationArchive(ids, archivedAt);
		deleteNotifications(ids);
		return ids.size();
	}

	private int archiveCompletedGroupBatch(LocalDateTime cutoff) {
		List<Long> ids = findArchivableGroupIds(cutoff, archiveProperties.resolveBatchSize());
		if (ids.isEmpty()) {
			return 0;
		}

		LocalDateTime archivedAt = LocalDateTime.now();
		insertNotificationGroupArchive(ids, archivedAt);
		deleteGroups(ids);
		return ids.size();
	}

	private List<Long> findArchivableNotificationIds(LocalDateTime cutoff, int limit) {
		return jdbcTemplate.queryForList("""
				SELECT id
				FROM notification
				WHERE created_at < ?
				  AND status IN (?, ?, ?)
				ORDER BY id
				LIMIT ?
				""",
			Long.class,
			Timestamp.valueOf(cutoff),
			TERMINAL_STATUSES.get(0),
			TERMINAL_STATUSES.get(1),
			TERMINAL_STATUSES.get(2),
			limit
		);
	}

	private List<Long> findArchivableGroupIds(LocalDateTime cutoff, int limit) {
		return jdbcTemplate.queryForList("""
				SELECT g.id
				FROM notification_group g
				WHERE g.created_at < ?
				  AND g.total_count = g.sent_count + g.failed_count
				  AND NOT EXISTS (
				      SELECT 1
				      FROM notification n
				      WHERE n.group_id = g.id
				  )
				ORDER BY g.id
				LIMIT ?
				""",
			Long.class,
			Timestamp.valueOf(cutoff),
			limit
		);
	}

	private void insertNotificationArchive(List<Long> ids, LocalDateTime archivedAt) {
		namedParameterJdbcTemplate.update("""
				INSERT INTO notification_archive (
				    id, group_id, receiver, status, sent_at, attempt_count,
				    fail_reason, created_at, updated_at, archived_at
				)
				SELECT
				    id, group_id, receiver, status, sent_at, attempt_count,
				    fail_reason, created_at, updated_at, :archivedAt
				FROM notification
				WHERE id IN (:ids)
				""",
			new MapSqlParameterSource()
				.addValue("ids", ids)
				.addValue("archivedAt", archivedAt)
		);
	}

	private void insertNotificationReadStatusArchive(List<Long> ids, LocalDateTime archivedAt) {
		namedParameterJdbcTemplate.update("""
				INSERT INTO notification_read_status_archive (
				    notification_id, read_at, archived_at
				)
				SELECT
				    notification_id, read_at, :archivedAt
				FROM notification_read_status
				WHERE notification_id IN (:ids)
				""",
			new MapSqlParameterSource()
				.addValue("ids", ids)
				.addValue("archivedAt", archivedAt)
		);
	}

	private void insertNotificationGroupArchive(List<Long> ids, LocalDateTime archivedAt) {
		namedParameterJdbcTemplate.update("""
				INSERT INTO notification_group_archive (
				    id, client_id, idempotency_key, sender, title, content,
				    group_type, channel_type, total_count, sent_count, failed_count,
				    created_at, updated_at, archived_at
				)
				SELECT
				    id, client_id, idempotency_key, sender, title, content,
				    group_type, channel_type, total_count, sent_count, failed_count,
				    created_at, updated_at, :archivedAt
				FROM notification_group
				WHERE id IN (:ids)
				""",
			new MapSqlParameterSource()
				.addValue("ids", ids)
				.addValue("archivedAt", archivedAt)
		);
	}

	private void deleteNotifications(List<Long> ids) {
		namedParameterJdbcTemplate.update(
			"DELETE FROM notification WHERE id IN (:ids)",
			new MapSqlParameterSource().addValue("ids", ids)
		);
	}

	private void deleteNotificationReadStatuses(List<Long> ids) {
		namedParameterJdbcTemplate.update(
			"DELETE FROM notification_read_status WHERE notification_id IN (:ids)",
			new MapSqlParameterSource().addValue("ids", ids)
		);
	}

	private void deleteGroups(List<Long> ids) {
		namedParameterJdbcTemplate.update(
			"DELETE FROM notification_group WHERE id IN (:ids)",
			new MapSqlParameterSource().addValue("ids", ids)
		);
		ids.forEach(groupDetailCacheRepository::evict);
	}

	private void ensureNextMonthPartition(String tableName, YearMonth nextMonth) {
		String partitionName = partitionName(nextMonth);
		Integer exists = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.PARTITIONS
				WHERE TABLE_SCHEMA = DATABASE()
				  AND TABLE_NAME = ?
				  AND PARTITION_NAME = ?
				""",
			Integer.class,
			tableName,
			partitionName
		);

		if (exists != null && exists > 0) {
			return;
		}

		int lessThan = partitionValue(nextMonth.plusMonths(1));
		String sql = """
			ALTER TABLE %s
			REORGANIZE PARTITION p_future INTO (
			    PARTITION %s VALUES LESS THAN (%d),
			    PARTITION p_future VALUES LESS THAN MAXVALUE
			)
			""".formatted(tableName, partitionName, lessThan);
		jdbcTemplate.execute(sql);
		log.info("Archive partition 생성: table={}, partition={}", tableName, partitionName);
	}

	private String partitionName(YearMonth yearMonth) {
		return "p%04d%02d".formatted(yearMonth.getYear(), yearMonth.getMonthValue());
	}

	private int partitionValue(YearMonth yearMonth) {
		return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
	}

	public record ArchiveRunResult(
		LocalDateTime cutoff,
		int archivedNotifications,
		int archivedGroups
	) {
		public boolean hasWork() {
			return archivedNotifications > 0 || archivedGroups > 0;
		}
	}
}
