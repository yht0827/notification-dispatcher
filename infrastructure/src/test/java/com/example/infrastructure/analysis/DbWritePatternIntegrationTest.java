package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.in.result.NotificationDispatchResult;
import com.example.application.service.NotificationCommandService;
import com.example.application.service.NotificationDispatchService;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.Outbox;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;

import jakarta.persistence.EntityManagerFactory;

@TestPropertySource(properties = {
	"spring.jpa.properties.hibernate.generate_statistics=true"
})
class DbWritePatternIntegrationTest extends IntegrationTestSupportNoTx {

	@Autowired
	private NotificationCommandService commandService;

	@Autowired
	private NotificationDispatchService dispatchService;

	@Autowired
	private NotificationGroupRepository groupRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Statistics statistics;

	@BeforeEach
	void setUp() {
		truncateTables();
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
	}

	@Test
	@DisplayName("request는 그룹 1건, 알림 N건, outbox N건을 저장한다")
	void request_persistsGroupNotificationsAndOutboxes() {
		SendCommand command = new SendCommand(
			"write-pattern-client",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			java.util.List.of("a@test.com", "b@test.com"),
			"idem-write-pattern"
		);

		NotificationCommandResult result = commandService.request(command);

		assertThat(result.totalCount()).isEqualTo(2);
		assertEntityStats(NotificationGroup.class, 1, 0, 0);
		assertEntityStats(Notification.class, 2, 0, 0);
		assertEntityStats(Outbox.class, 2, 0, 0);
	}

	@Test
	@DisplayName("dispatch 성공은 notification과 group을 갱신한다")
	void dispatch_success_updatesNotificationAndGroup() {
		Long notificationId = createSingleNotification();
		statistics.clear();

		Notification detached = notificationRepository.findById(notificationId).orElseThrow();
		NotificationDispatchResult result = dispatchService.dispatch(detached);

		assertThat(result.isSuccess()).isTrue();
		assertEntityStats(Notification.class, 0, 1, 0);
		assertEntityStats(NotificationGroup.class, 0, 1, 0);
	}

	@Test
	@DisplayName("markAsFailed는 notification과 group을 갱신한다")
	void markAsFailed_updatesNotificationAndGroup() {
		Long notificationId = createSingleNotification();
		Notification detached = notificationRepository.findById(notificationId).orElseThrow();
		detached.startSending();
		notificationRepository.save(detached);

		statistics.clear();
		dispatchService.markAsFailed(notificationId, "send failed");

		assertEntityStats(Notification.class, 0, 1, 0);
		assertEntityStats(NotificationGroup.class, 0, 1, 0);
	}

	private Long createSingleNotification() {
		SendCommand command = new SendCommand(
			"dispatch-client",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			java.util.List.of("one@test.com"),
			"idem-dispatch"
		);

		NotificationCommandResult result = commandService.request(command);
		NotificationGroup group = groupRepository.findByIdWithNotifications(result.groupId()).orElseThrow();
		return group.getNotifications().getFirst().getId();
	}

	private void assertEntityStats(Class<?> entityType, long inserts, long updates, long deletes) {
		EntityStatistics entityStatistics = statistics.getEntityStatistics(entityType.getName());
		assertThat(entityStatistics.getInsertCount()).isEqualTo(inserts);
		assertThat(entityStatistics.getUpdateCount()).isEqualTo(updates);
		assertThat(entityStatistics.getDeleteCount()).isEqualTo(deletes);
	}

	private void truncateTables() {
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
		jdbcTemplate.execute("TRUNCATE TABLE outbox");
		jdbcTemplate.execute("TRUNCATE TABLE notification");
		jdbcTemplate.execute("TRUNCATE TABLE notification_group");
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
	}
}
