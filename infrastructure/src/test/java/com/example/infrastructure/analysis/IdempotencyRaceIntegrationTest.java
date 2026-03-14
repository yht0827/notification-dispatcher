package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.application.port.in.NotificationWriteUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;

class IdempotencyRaceIntegrationTest extends IntegrationTestSupportNoTx {

	private static final String CLIENT_ID = "idem-race-client";
	private static final String IDEMPOTENCY_KEY = "idem-race-key";

	@Autowired
	private NotificationWriteUseCase commandUseCase;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private NotificationGroupRepository groupRepository;

	@BeforeEach
	void setUp() {
		truncateTables();
		ensureIdempotencyUniqueIndex();
	}

	@Test
	@DisplayName("동일 clientId/idempotencyKey 동시 요청 경쟁 시 loser도 기존 groupId를 반환한다")
	void request_withSameIdempotencyKeyRace_returnsSameGroupToBothCallers() throws Exception {
		CountDownLatch lookupBarrier = new CountDownLatch(2);
		doAnswer(invocation -> {
			Optional<NotificationGroup> result = (Optional<NotificationGroup>)invocation.callRealMethod();
			String clientId = invocation.getArgument(0);
			String idempotencyKey = invocation.getArgument(1);
			if (CLIENT_ID.equals(clientId) && IDEMPOTENCY_KEY.equals(idempotencyKey)) {
				lookupBarrier.countDown();
				assertThat(lookupBarrier.await(3, TimeUnit.SECONDS)).isTrue();
			}
			return result;
		}).when(groupRepository).findByClientIdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY);

		SendCommand command = new SendCommand(
			CLIENT_ID,
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			List.of("user1@test.com", "user2@test.com"),
			IDEMPOTENCY_KEY,
			null
		);

		Callable<Object> task = () -> {
			try {
				return commandUseCase.request(command);
			} catch (Exception e) {
				return e;
			}
		};

		List<Object> results = executeConcurrently(task, task);

		assertThat(results.stream().filter(NotificationResultPredicates::isCommandSuccess).count()).isEqualTo(2);
		assertThat(results.stream().filter(NotificationResultPredicates::isException).count()).isZero();
		assertThat(results.stream()
			.filter(NotificationResultPredicates::isCommandSuccess)
			.map(NotificationCommandResult.class::cast)
			.map(NotificationCommandResult::groupId)
			.distinct()
			.count()).isEqualTo(1);
		assertThat(groupRepository.findByClientIdAndIdempotencyKey(CLIENT_ID, IDEMPOTENCY_KEY)).isPresent();
		assertThat(countRows("notification_group")).isEqualTo(1);
		assertThat(countRows("notification")).isEqualTo(2);
	}

	@Test
	@DisplayName("동일 clientId/idempotencyKey 대량 경쟁 N=10/20/50에서도 모두 같은 groupId를 반환한다")
	void request_withSameIdempotencyKeyRace_scalesToHigherConcurrency() throws Exception {
		for (int requestCount : List.of(10, 20, 50)) {
			truncateTables();
			String clientId = CLIENT_ID + "-" + requestCount;
			String idempotencyKey = IDEMPOTENCY_KEY + "-" + requestCount;

			SendCommand command = new SendCommand(
				clientId,
				"sender",
				"title",
				"content",
				ChannelType.EMAIL,
				List.of("user1@test.com", "user2@test.com"),
				idempotencyKey,
				null
			);

			List<Object> results = executeConcurrently(buildTasks(command, requestCount), 15);

			assertThat(results.stream().filter(NotificationResultPredicates::isCommandSuccess).count())
				.isEqualTo(requestCount);
			assertThat(results.stream().filter(NotificationResultPredicates::isException).count()).isZero();
			assertThat(results.stream()
				.filter(NotificationResultPredicates::isCommandSuccess)
				.map(NotificationCommandResult.class::cast)
				.map(NotificationCommandResult::groupId)
				.distinct()
				.count()).isEqualTo(1);
			assertThat(groupRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)).isPresent();
			assertThat(countRows("notification_group")).isEqualTo(1);
			assertThat(countRows("notification")).isEqualTo(2);
		}
	}

	private List<Callable<Object>> buildTasks(SendCommand command, int requestCount) {
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < requestCount; i++) {
			tasks.add(() -> {
				try {
					return commandUseCase.request(command);
				} catch (Exception e) {
					return e;
				}
			});
		}
		return tasks;
	}

	private List<Object> executeConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
		return executeConcurrently(List.of(first, second), 5);
	}

	private List<Object> executeConcurrently(List<Callable<Object>> tasks, int timeoutSeconds) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch startLatch = new CountDownLatch(1);
		try {
			List<Callable<Object>> wrapped = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				wrapped.add(() -> {
					startLatch.await();
					return task.call();
				});
			}
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> callable : wrapped) {
				futures.add(executor.submit(callable));
			}
			startLatch.countDown();

			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(3, TimeUnit.SECONDS);
		}
	}

	private List<Object> executeConcurrently(Callable<Object> first, Callable<Object> second, int timeoutSeconds) throws
		Exception {
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
				results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
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

	private void truncateTables() {
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
		jdbcTemplate.execute("TRUNCATE TABLE outbox");
		jdbcTemplate.execute("TRUNCATE TABLE notification");
		jdbcTemplate.execute("TRUNCATE TABLE notification_group");
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
	}

	private void ensureIdempotencyUniqueIndex() {
		Integer indexCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notification_group' AND index_name = 'idx_notification_group_client_idempotency_key'",
			Integer.class
		);
		if (indexCount == null || indexCount == 0) {
			jdbcTemplate.execute(
				"CREATE UNIQUE INDEX idx_notification_group_client_idempotency_key ON notification_group (client_id, idempotency_key)"
			);
		}
	}

	private static final class NotificationResultPredicates {

		private NotificationResultPredicates() {
		}

		private static boolean isCommandSuccess(Object value) {
			return value instanceof NotificationCommandResult;
		}

		private static boolean isException(Object value) {
			return value instanceof Exception;
		}
	}
}
