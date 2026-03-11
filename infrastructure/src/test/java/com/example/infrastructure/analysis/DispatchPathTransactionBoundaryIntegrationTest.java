package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;

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
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.result.SendResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.service.NotificationWriteService;
import com.example.application.service.NotificationDispatchService;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;

import jakarta.persistence.EntityManagerFactory;

@TestPropertySource(properties = {
	"spring.jpa.properties.hibernate.generate_statistics=true"
})
class DispatchPathTransactionBoundaryIntegrationTest extends IntegrationTestSupportNoTx {

	@Autowired
	private NotificationWriteService commandService;

	@Autowired
	private NotificationDispatchService dispatchService;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationGroupRepository groupRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

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
	@DisplayName("현재 dispatch 경로는 메시지당 개별 트랜잭션이라 group update가 메시지 수만큼 발생한다")
	void dispatchLikeProcessing_updatesGroupPerMessageTransaction() {
		List<Long> notificationIds = createNotifications(3);
		statistics.clear();

		for (Long notificationId : notificationIds) {
			Notification detached = notificationRepository.findById(notificationId).orElseThrow();
			List<com.example.application.port.in.result.BatchDispatchResult> results =
				dispatchService.dispatchBatch(List.of(detached));
			assertThat(results).singleElement().satisfies(r -> assertThat(r.isSuccess()).isTrue());
		}

		assertEntityStats(Notification.class, 0, 3, 0);
		assertEntityStats(NotificationGroup.class, 0, 3, 0);
	}

	@Test
	@DisplayName("여러 알림을 한 트랜잭션에서 처리하면 group update는 1회로 줄어든다")
	void bulkProcessingInSingleTransaction_updatesGroupOnce() {
		Long groupId = createGroupId(3);
		statistics.clear();

		transactionTemplate.executeWithoutResult(status -> {
			NotificationGroup group = groupRepository.findByIdWithNotifications(groupId).orElseThrow();
			for (Notification notification : group.getNotifications()) {
				notification.startSending();
				notification.markAsSent();
			}
		});

		assertEntityStats(Notification.class, 0, 3, 0);
		assertEntityStats(NotificationGroup.class, 0, 1, 0);
	}

	private List<Long> createNotifications(int receiverCount) {
		NotificationGroup group = groupRepository.findByIdWithNotifications(createGroupId(receiverCount)).orElseThrow();
		return group.getNotifications().stream()
			.map(Notification::getId)
			.toList();
	}

	private Long createGroupId(int receiverCount) {
		List<String> receivers = java.util.stream.IntStream.range(0, receiverCount)
			.mapToObj(i -> "dispatch-" + i + "@example.com")
			.toList();

		NotificationCommandResult result = commandService.request(
			new SendCommand(
				"dispatch-boundary-client",
				"sender",
				"title",
				"content",
				ChannelType.EMAIL,
				receivers,
				"dispatch-boundary-" + receiverCount,
				null
			)
		);
		return result.groupId();
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
