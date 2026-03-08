package com.example.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.support.IntegrationTestSupport;

class NotificationReadStatusRepositoryTest extends IntegrationTestSupport {

	@Autowired
	private NotificationReadStatusRepository notificationReadStatusRepository;

	@Autowired
	private NotificationGroupRepository notificationGroupRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("markAsRead는 중복 호출돼도 row를 1건만 유지한다")
	void markAsRead_keepsSingleRowOnDuplicateCall() {
		Notification notification = createNotification("user1@example.com");
		LocalDateTime firstReadAt = LocalDateTime.of(2026, 3, 8, 12, 0);
		LocalDateTime secondReadAt = firstReadAt.plusMinutes(5);

		notificationReadStatusRepository.markAsRead(notification.getId(), firstReadAt);
		notificationReadStatusRepository.markAsRead(notification.getId(), secondReadAt);

		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM notification_read_status WHERE notification_id = ?",
			Integer.class,
			notification.getId()
		);

		assertThat(count).isEqualTo(1);
		assertThat(notificationReadStatusRepository.findReadAtByNotificationId(notification.getId()))
			.isEqualTo(firstReadAt);
	}

	@Test
	@DisplayName("markAllAsRead는 이미 읽은 알림을 제외한 신규 건수만 반환한다")
	void markAllAsRead_returnsOnlyInsertedCount() {
		Notification first = createNotification("user1@example.com");
		Notification second = createNotification("user2@example.com");
		LocalDateTime firstReadAt = LocalDateTime.of(2026, 3, 8, 12, 0);
		LocalDateTime groupReadAt = firstReadAt.plusMinutes(10);

		notificationReadStatusRepository.markAsRead(first.getId(), firstReadAt);

		int insertedCount = notificationReadStatusRepository.markAllAsRead(
			List.of(first.getId(), second.getId()),
			groupReadAt
		);

		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM notification_read_status WHERE notification_id IN (?, ?)",
			Integer.class,
			first.getId(),
			second.getId()
		);

		assertThat(insertedCount).isEqualTo(1);
		assertThat(count).isEqualTo(2);
		assertThat(notificationReadStatusRepository.findReadAtByNotificationId(first.getId()))
			.isEqualTo(firstReadAt);
		assertThat(notificationReadStatusRepository.findReadAtByNotificationId(second.getId()))
			.isEqualTo(groupReadAt);
	}

	private Notification createNotification(String receiver) {
		NotificationGroup group = NotificationGroup.create(
			"test-service",
			"MyShop",
			"테스트",
			"테스트 내용",
			ChannelType.EMAIL,
			1
		);
		Notification notification = group.addNotification(receiver);
		notificationGroupRepository.save(group);
		return notification;
	}
}
