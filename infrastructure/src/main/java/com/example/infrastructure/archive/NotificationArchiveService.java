package com.example.infrastructure.archive;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

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

	public ArchiveRunResult archiveExpiredData() {
		// 보존 기간(retention-days) 이전 데이터를 cutoff 기준으로 아카이빙
		LocalDateTime cutoff = LocalDateTime.now().minusDays(archiveProperties.resolveRetentionDays());

		// 1. 알림 배치 아카이빙: 처리할 데이터가 없을 때까지 반복
		int archivedNotifications = 0;
		while (true) {
			Integer batchArchived = transactionTemplate.execute(status -> archiveNotificationBatch(cutoff));
			int count = batchArchived == null ? 0 : batchArchived;
			if (count == 0) {
				break;
			}
			archivedNotifications += count;
		}

		// 2. 알림 그룹 배치 아카이빙: 알림이 모두 제거된 완료 그룹만 대상
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

	private int archiveNotificationBatch(LocalDateTime cutoff) {
		List<Long> ids = findArchivableNotificationIds(cutoff, archiveProperties.resolveBatchSize());
		if (ids.isEmpty()) {
			return 0;
		}

		LocalDateTime archivedAt = LocalDateTime.now();
		// 1. 읽음 상태 먼저 아카이빙 후 삭제 (FK 순서 고려)
		insertNotificationReadStatusArchive(ids, archivedAt);
		deleteNotificationReadStatuses(ids);
		// 2. 알림 아카이빙 후 원본 삭제
		insertNotificationArchive(ids, archivedAt);
		deleteNotifications(ids);
		return ids.size();
	}

	private int archiveCompletedGroupBatch(LocalDateTime cutoff) {
		List<Long> ids = findArchivableGroupIds(cutoff, archiveProperties.resolveBatchSize());
		if (ids.isEmpty()) {
			return 0;
		}

		// 알림 그룹 아카이빙 후 원본 삭제
		LocalDateTime archivedAt = LocalDateTime.now();
		insertNotificationGroupArchive(ids, archivedAt);
		deleteGroups(ids);
		return ids.size();
	}

	private List<Long> findArchivableNotificationIds(LocalDateTime cutoff, int limit) {
		// cutoff 이전에 생성된 종료 상태(SENT/FAILED/CANCELED) 알림 ID 조회
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
		// 완료 조건: total = sent + failed이고 연결된 notification이 없는 그룹
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
