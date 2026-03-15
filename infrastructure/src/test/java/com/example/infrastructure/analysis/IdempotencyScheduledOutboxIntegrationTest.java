package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.example.application.port.in.NotificationWriteUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.domain.notification.ChannelType;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;

@TestPropertySource(properties = {
	"notification.messaging.enabled=true"
})
class IdempotencyScheduledOutboxIntegrationTest extends IntegrationTestSupportNoTx {

	private static final String CLIENT_ID = "idem-scheduled-client";
	private static final String IDEMPOTENCY_KEY = "idem-scheduled-key";

	@Autowired
	private NotificationWriteUseCase commandUseCase;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		truncateTables();
	}

	@Test
	@DisplayName("예약 발송 + 동일 idempotencyKey 경쟁 시 group 1건, notification/outbox는 fan-out 수만큼만 생성된다")
	void request_withScheduledAtAndSameIdempotencyKeyRace_createsSingleGroupAndExactOutboxes() throws Exception {
		LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(5);
		SendCommand command = new SendCommand(
			CLIENT_ID,
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			List.of(
				"user1@test.com",
				"user2@test.com",
				"user3@test.com",
				"user4@test.com",
				"user5@test.com"
			),
			IDEMPOTENCY_KEY,
			scheduledAt
		);

		Callable<Object> task = () -> {
			try {
				return commandUseCase.request(command);
			} catch (Exception e) {
				return e;
			}
		};

		List<Object> results = executeConcurrently(task, task);

		assertThat(results.stream().filter(NotificationCommandResult.class::isInstance).count()).isEqualTo(2);
		assertThat(results.stream().filter(Exception.class::isInstance).count()).isZero();
		assertThat(results.stream()
			.filter(NotificationCommandResult.class::isInstance)
			.map(NotificationCommandResult.class::cast)
			.map(NotificationCommandResult::groupId)
			.distinct()
			.count()).isEqualTo(1);

		assertThat(countRows("notification_group")).isEqualTo(1);
		assertThat(countRows("notification")).isEqualTo(5);
		assertThat(countRows("outbox")).isEqualTo(5);
		assertThat(countScheduledOutboxRows()).isEqualTo(5);
	}

	private List<Object> executeConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch startLatch = new CountDownLatch(1);
		try {
			List<Callable<Object>> wrapped = List.of(
				() -> {
					startLatch.await();
					return first.call();
				},
				() -> {
					startLatch.await();
					return second.call();
				}
			);
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> callable : wrapped) {
				futures.add(executor.submit(callable));
			}
			startLatch.countDown();

			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(10, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(3, TimeUnit.SECONDS);
		}
	}

	private int countRows(String table) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
		return count == null ? 0 : count;
	}

	private int countScheduledOutboxRows() {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM outbox WHERE scheduled_at IS NOT NULL",
			Integer.class
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
