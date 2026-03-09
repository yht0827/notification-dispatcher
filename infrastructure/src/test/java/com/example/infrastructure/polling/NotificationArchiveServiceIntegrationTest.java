package com.example.infrastructure.polling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.out.cache.NotificationDetailCacheRepository;
import com.example.application.port.out.cache.NotificationGroupDetailCacheRepository;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;

class NotificationArchiveServiceIntegrationTest extends IntegrationTestSupportNoTx {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private NotificationGroupRepository groupRepository;

	private NotificationArchiveService archiveService;

	@BeforeEach
	void setUp() {
		archiveService = new NotificationArchiveService(
			jdbcTemplate,
			namedParameterJdbcTemplate,
			new ArchiveProperties(true, false, 1000, 7, null, null),
			new TransactionTemplate(transactionManager),
			new NotificationDetailCacheRepository() {
				@Override
				public boolean enabled() {
					return false;
				}

				@Override
				public java.util.Optional<com.example.application.port.in.result.NotificationResult> get(Long notificationId) {
					return java.util.Optional.empty();
				}

				@Override
				public void put(Long notificationId, com.example.application.port.in.result.NotificationResult detail) {
				}

				@Override
				public void evict(Long notificationId) {
				}
			},
			new NotificationGroupDetailCacheRepository() {
				@Override
				public boolean enabled() {
					return false;
				}

				@Override
				public java.util.Optional<com.example.application.port.in.result.NotificationGroupDetailResult> get(Long groupId) {
					return java.util.Optional.empty();
				}

				@Override
				public void put(Long groupId, com.example.application.port.in.result.NotificationGroupDetailResult detail) {
				}

				@Override
				public void evict(Long groupId) {
				}
			}
		);

		jdbcTemplate.execute("DROP TABLE IF EXISTS notification_archive");
		jdbcTemplate.execute("DROP TABLE IF EXISTS notification_group_archive");
		jdbcTemplate.execute("DROP TABLE IF EXISTS notification_read_status_archive");
		jdbcTemplate.execute("DROP TABLE IF EXISTS notification_read_status");
		jdbcTemplate.execute("""
			CREATE TABLE notification_read_status
			(
			    notification_id BIGINT NOT NULL PRIMARY KEY,
			    read_at DATETIME(6) NOT NULL
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE notification_read_status_archive
			(
			    notification_id BIGINT NOT NULL PRIMARY KEY,
			    read_at DATETIME(6) NOT NULL,
			    archived_at DATETIME(6) NOT NULL
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE notification_archive
			(
			    id BIGINT NOT NULL PRIMARY KEY,
			    group_id BIGINT NULL,
			    receiver VARCHAR(255) NOT NULL,
			    status VARCHAR(50) NOT NULL,
			    sent_at DATETIME(6) NULL,
			    attempt_count INT NOT NULL DEFAULT 0,
			    fail_reason VARCHAR(500) NULL,
			    created_at DATETIME(6) NOT NULL,
			    updated_at DATETIME(6) NOT NULL,
			    archived_at DATETIME(6) NOT NULL
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE notification_group_archive
			(
			    id BIGINT NOT NULL PRIMARY KEY,
			    client_id VARCHAR(255) NOT NULL,
			    idempotency_key VARCHAR(255) NULL,
			    sender VARCHAR(255) NOT NULL,
			    title VARCHAR(255) NOT NULL,
			    content TEXT NOT NULL,
			    group_type VARCHAR(50) NOT NULL,
			    channel_type VARCHAR(50) NOT NULL,
			    total_count INT NOT NULL DEFAULT 0,
			    sent_count INT NOT NULL DEFAULT 0,
			    failed_count INT NOT NULL DEFAULT 0,
			    created_at DATETIME(6) NOT NULL,
			    updated_at DATETIME(6) NOT NULL,
			    archived_at DATETIME(6) NOT NULL
			)
			""");
	}

	@Test
	@DisplayName("7일 지난 terminal notification과 completed group만 archive로 이동한다")
	void archiveExpiredData_movesOnlyEligibleRows() {
		NotificationGroup oldCompleted = NotificationGroup.create(
			"archive-client",
			"archive-idem-1",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			2
		);
		Notification first = oldCompleted.addNotification("one@example.com");
		Notification second = oldCompleted.addNotification("two@example.com");
		first.startSending();
		first.markAsSent();
		second.startSending();
		second.markAsFailed("fail");
		NotificationGroup savedOldCompleted = groupRepository.save(oldCompleted);

		NotificationGroup oldPending = NotificationGroup.create(
			"archive-client",
			"archive-idem-2",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			1
		);
		oldPending.addNotification("pending@example.com");
		NotificationGroup savedOldPending = groupRepository.save(oldPending);

		NotificationGroup recentCompleted = NotificationGroup.create(
			"archive-client",
			"archive-idem-3",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			1
		);
		Notification recent = recentCompleted.addNotification("recent@example.com");
		recent.startSending();
		recent.markAsSent();
		NotificationGroup savedRecentCompleted = groupRepository.save(recentCompleted);

		jdbcTemplate.update(
			"INSERT INTO notification_read_status (notification_id, read_at) VALUES (?, ?)",
			first.getId(),
			LocalDateTime.now().minusDays(1)
		);
		jdbcTemplate.update(
			"INSERT INTO notification_read_status (notification_id, read_at) VALUES (?, ?)",
			recent.getId(),
			LocalDateTime.now().minusHours(3)
		);

		LocalDateTime oldTimestamp = LocalDateTime.now().minusDays(8);
		backdateGroupAndNotifications(savedOldCompleted.getId(), oldTimestamp);
		backdateGroupAndNotifications(savedOldPending.getId(), oldTimestamp);

		NotificationArchiveService.ArchiveRunResult result = archiveService.archiveExpiredData();

		assertThat(result.archivedNotifications()).isEqualTo(2);
		assertThat(result.archivedGroups()).isEqualTo(1);

		assertThat(count("notification_archive")).isEqualTo(2);
		assertThat(count("notification_group_archive")).isEqualTo(1);
		assertThat(count("notification_read_status_archive")).isEqualTo(1);

		assertThat(countById("notification_group", savedOldCompleted.getId())).isZero();
		assertThat(countById("notification_group", savedOldPending.getId())).isOne();
		assertThat(countById("notification_group", savedRecentCompleted.getId())).isOne();

		assertThat(countByGroupId("notification", savedOldCompleted.getId())).isZero();
		assertThat(countByGroupId("notification", savedOldPending.getId())).isOne();
		assertThat(countByGroupId("notification", savedRecentCompleted.getId())).isOne();
		assertThat(countReadStatusByNotificationId(first.getId())).isZero();
		assertThat(countReadStatusByNotificationId(recent.getId())).isOne();
	}

	private void backdateGroupAndNotifications(Long groupId, LocalDateTime createdAt) {
		jdbcTemplate.update(
			"UPDATE notification_group SET created_at = ?, updated_at = ? WHERE id = ?",
			createdAt,
			createdAt,
			groupId
		);
		jdbcTemplate.update(
			"UPDATE notification SET created_at = ?, updated_at = ? WHERE group_id = ?",
			createdAt,
			createdAt,
			groupId
		);
	}

	private int count(String tableName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
	}

	private int countById(String tableName, Long id) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + tableName + " WHERE id = ?",
			Integer.class,
			id
		);
	}

	private int countByGroupId(String tableName, Long groupId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + tableName + " WHERE group_id = ?",
			Integer.class,
			groupId
		);
	}

	private int countReadStatusByNotificationId(Long notificationId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM notification_read_status WHERE notification_id = ?",
			Integer.class,
			notificationId
		);
	}
}
