package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import org.hibernate.SessionFactory;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.DispatchResult;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.port.out.SendResult;
import com.example.application.service.NotificationDispatchService;
import com.example.application.service.NotificationWriteService;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.outbox.Outbox;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;

import jakarta.persistence.EntityManagerFactory;

@TestPropertySource(properties = {
	"spring.jpa.properties.hibernate.generate_statistics=true"
})
class DbWritePatternIntegrationTest extends IntegrationTestSupportNoTx {

	@Autowired
	private NotificationWriteService commandService;

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

	@MockitoBean
	private NotificationSender notificationSender;

	private Statistics statistics;

	@BeforeEach
	void setUp() {
		truncateTables();
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		given(notificationSender.send(any(Notification.class))).willReturn(SendResult.success());
	}

	@Test
	@DisplayName("request는 그룹만 JPA로 저장하고 알림 row는 bulk insert로 저장한다")
	void request_persistsGroupNotificationsAndOutboxes() {
		SendCommand command = new SendCommand(
			"write-pattern-client",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			java.util.List.of("a@test.com", "b@test.com"),
			"idem-write-pattern",
			null
		);

		NotificationCommandResult result = commandService.request(command);

		assertThat(result.totalCount()).isEqualTo(2);
		assertEntityStats(NotificationGroup.class, 1, 0, 0);
		assertEntityStats(Notification.class, 0, 0, 0);
		assertThat(countRows("notification")).isEqualTo(2);
		assertEntityStats(Outbox.class, 0, 0, 0);
		assertThat(countRows("outbox")).isEqualTo(1);
	}

	@Test
	@DisplayName("dispatch 단건 성공은 notification과 group을 갱신한다")
	void dispatch_success_updatesNotificationAndGroup() {
		Long notificationId = createSingleNotification();

		DispatchResult result = dispatchService.dispatch(notificationId);

		assertThat(result.isSuccess()).isTrue();
		assertThat(countNotificationsByStatus("SENT")).isEqualTo(1);
		assertThat(countSentNotifications()).isEqualTo(1);
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

	@Test
	@DisplayName("dispatch 단건 성공은 상태 전이를 batch write로 반영하고 group 카운터를 집계한다")
	void dispatch_success_appliesStatusTransitionsAndGroupCounts() {
		java.util.List<Long> notificationIds = createNotifications(3);
		Long groupId = groupRepository.findByIdWithNotifications(createGroupIdForExistingNotifications(notificationIds))
			.orElseThrow()
			.getId();
		statistics.clear();

		for (Long id : notificationIds) {
			DispatchResult result = dispatchService.dispatch(id);
			assertThat(result.isSuccess()).isTrue();
		}

		assertThat(countNotificationsByStatus("SENT")).isEqualTo(3);
		assertThat(sumAttemptCount()).isEqualTo(3);
		assertThat(countSentNotifications()).isEqualTo(3);
		assertThat(groupSentCount(groupId)).isEqualTo(3);
		assertThat(groupFailedCount(groupId)).isZero();
	}

	private Long createSingleNotification() {
		SendCommand command = new SendCommand(
			"dispatch-client",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			java.util.List.of("one@test.com"),
			"idem-dispatch",
			null
		);

		NotificationCommandResult result = commandService.request(command);
		NotificationGroup group = groupRepository.findByIdWithNotifications(result.groupId()).orElseThrow();
		return group.getNotifications().getFirst().getId();
	}

	private java.util.List<Long> createNotifications(int receiverCount) {
		NotificationGroup group = groupRepository.findByIdWithNotifications(createGroupId(receiverCount)).orElseThrow();
		return group.getNotifications().stream()
			.map(Notification::getId)
			.toList();
	}

	private Long createGroupIdForExistingNotifications(java.util.List<Long> notificationIds) {
		return notificationRepository.findById(notificationIds.getFirst())
			.orElseThrow()
			.getGroup()
			.getId();
	}

	private Long createGroupId(int receiverCount) {
		java.util.List<String> receivers = java.util.stream.IntStream.range(0, receiverCount)
			.mapToObj(index -> "batch-" + index + "@example.com")
			.toList();

		NotificationCommandResult result = commandService.request(new SendCommand(
			"batch-pattern-client",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			receivers,
			"idem-batch-" + receiverCount,
			null
		));
		return result.groupId();
	}

	private void assertEntityStats(Class<?> entityType, long inserts, long updates, long deletes) {
		EntityStatistics entityStatistics = statistics.getEntityStatistics(entityType.getName());
		assertThat(entityStatistics.getInsertCount()).isEqualTo(inserts);
		assertThat(entityStatistics.getUpdateCount()).isEqualTo(updates);
		assertThat(entityStatistics.getDeleteCount()).isEqualTo(deletes);
	}

	private int countNotificationsByStatus(String status) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM notification WHERE status = ?",
			Integer.class,
			status
		);
		return count == null ? 0 : count;
	}

	private int countRows(String table) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
		return count == null ? 0 : count;
	}

	private int sumAttemptCount() {
		Integer sum = jdbcTemplate.queryForObject(
			"SELECT COALESCE(SUM(attempt_count), 0) FROM notification",
			Integer.class
		);
		return sum == null ? 0 : sum;
	}

	private int countSentNotifications() {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM notification WHERE sent_at IS NOT NULL",
			Integer.class
		);
		return count == null ? 0 : count;
	}

	private int groupSentCount(Long groupId) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT sent_count FROM notification_group WHERE id = ?",
			Integer.class,
			groupId
		);
		return count == null ? 0 : count;
	}

	private int groupFailedCount(Long groupId) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT failed_count FROM notification_group WHERE id = ?",
			Integer.class,
			groupId
		);
		return count == null ? 0 : count;
	}

	private void truncateTables() {
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
		jdbcTemplate.execute("TRUNCATE TABLE outbox");
		jdbcTemplate.execute("TRUNCATE TABLE notification");
		jdbcTemplate.execute("TRUNCATE TABLE notification_group");
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
	}
}
