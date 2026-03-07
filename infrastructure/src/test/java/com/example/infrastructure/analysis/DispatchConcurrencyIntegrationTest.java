package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.api.RedissonClient;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.port.out.result.SendResult;
import com.example.application.service.NotificationCommandService;
import com.example.application.service.NotificationDispatchService;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStatus;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.inbound.RabbitMQRecordHandler;
import com.example.infrastructure.repository.NotificationJpaRepository;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;

class DispatchConcurrencyIntegrationTest extends IntegrationTestSupportNoTx {

	@Autowired
	private NotificationCommandService commandService;

	@Autowired
	private NotificationDispatchService dispatchService;

	@Autowired
	private DispatchLockManager dispatchLockManager;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationGroupRepository groupRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private NotificationJpaRepository notificationJpaRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private RedissonClient redissonClient;

	@MockBean
	private NotificationSender notificationSender;

	private RabbitMQRecordHandler recordHandler;

	@BeforeEach
	void setUp() {
		truncateTables();
		recordHandler = new RabbitMQRecordHandler(
			notificationRepository,
			dispatchService,
			new NotificationRabbitProperties(
				"notification.work",
				"notification.work.exchange",
				"notification.wait",
				"notification.dlq",
				"notification.dlq.exchange",
				3,
				5000,
				1,
				10,
				1,
				null,
				false,
				50,
				200
			),
			dispatchLockManager
		);
	}

	@Test
	@DisplayName("optimistic lock만으로는 최종 충돌은 막지만 외부 전송 중복까지 막지는 못한다")
	void dispatch_withOptimisticLock_allowsDuplicateExternalSendBeforeConflict() throws Exception {
		Long notificationId = createSingleNotification("optimistic-client", "idem-optimistic");
		CountDownLatch senderEntered = new CountDownLatch(1);

		when(notificationSender.send(any())).thenAnswer(invocation -> {
			senderEntered.countDown();
			Thread.sleep(150);
			return SendResult.success();
		});

		List<Object> results = executeConcurrently(List.of(
			optimisticDispatchTask(notificationId),
			optimisticDispatchTask(notificationId)
		));

		assertThat(senderEntered.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(results.stream().filter(NotificationResultPredicates::isDispatchSuccess).count()).isEqualTo(1);
		assertThat(results.stream().filter(NotificationResultPredicates::isOptimisticLockFailure).count()).isEqualTo(1);
		verify(notificationSender, times(2)).send(any());

		Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	@DisplayName("pessimistic lock 경로는 동일 notificationId 중복 처리 시 외부 발송을 1회로 제한한다")
	void dispatch_withPessimisticLock_processesSameNotificationOnlyOnce() throws Exception {
		Long notificationId = createSingleNotification("pessimistic-client", "idem-pessimistic");
		CountDownLatch senderEntered = new CountDownLatch(1);

		when(notificationSender.send(any())).thenAnswer(invocation -> {
			senderEntered.countDown();
			Thread.sleep(150);
			return SendResult.success();
		});

		List<Object> results = executeConcurrently(List.of(
			pessimisticDispatchTask(notificationId),
			pessimisticDispatchTask(notificationId)
		));

		assertThat(senderEntered.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(results).allMatch(NotificationResultPredicates::isDispatchSuccess);
		verify(notificationSender, times(1)).send(any());

		Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	@DisplayName("분산락 + optimistic 조합은 동일 notificationId 중복 처리 시 실제 발송을 1회로 제한한다")
	void recordHandler_withDistributedLock_processesSameNotificationOnlyOnce() throws Exception {
		Long notificationId = createSingleNotification("lock-client", "idem-lock");
		CountDownLatch senderEntered = new CountDownLatch(1);

		when(notificationSender.send(any())).thenAnswer(invocation -> {
			senderEntered.countDown();
			Thread.sleep(200);
			return SendResult.success();
		});

		Callable<Object> task = () -> {
			try {
				recordHandler.process(notificationId, 0);
				return "ok";
			} catch (Exception e) {
				return e;
			}
		};

		List<Object> results = executeConcurrently(task, task);

		assertThat(senderEntered.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(results).allMatch(NotificationResultPredicates::isNonExceptional);
		verify(notificationSender, times(1)).send(any());

		Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	@DisplayName("hot-key 분포에서 optimistic 경로는 충돌 후에도 시도 수만큼 외부 발송이 중복된다")
	void hotKey_withOptimisticLock_duplicatesExternalSendPerAttempt() throws Exception {
		List<Long> notificationIds = createNotifications(
			"hot-optimistic-client",
			"idem-hot-optimistic",
			List.of("hot-a@test.com", "hot-b@test.com", "cold@test.com")
		);
		List<Long> attempts = createHotKeyAttempts(notificationIds);

		when(notificationSender.send(any())).thenAnswer(invocation -> {
			Thread.sleep(150);
			return SendResult.success();
		});

		List<Object> results = executeOptimisticHotKeyAttempts(attempts);

		assertThat(results.stream().filter(NotificationResultPredicates::isDispatchSuccess).count())
			.isEqualTo(notificationIds.size());
		assertThat(results.stream().filter(NotificationResultPredicates::isOptimisticLockFailure).count())
			.isEqualTo(attempts.size() - notificationIds.size());
		verify(notificationSender, times(attempts.size())).send(any());
		assertNotificationsSent(notificationIds);
	}

	@Test
	@DisplayName("hot-key 분포에서 pessimistic lock 경로는 고유 notification 수만큼만 발송한다")
	void hotKey_withPessimisticLock_limitsExternalSendToUniqueNotifications() throws Exception {
		List<Long> notificationIds = createNotifications(
			"hot-pessimistic-client",
			"idem-hot-pessimistic",
			List.of("hot-a@test.com", "hot-b@test.com", "cold@test.com")
		);
		List<Long> attempts = createHotKeyAttempts(notificationIds);

		when(notificationSender.send(any())).thenAnswer(invocation -> {
			Thread.sleep(150);
			return SendResult.success();
		});

		List<Object> results = executeConcurrently(
			attempts.stream().map(this::pessimisticDispatchTask).toList()
		);

		assertThat(results).allMatch(NotificationResultPredicates::isDispatchSuccess);
		verify(notificationSender, times(notificationIds.size())).send(any());
		assertNotificationsSent(notificationIds);
	}

	@Test
	@DisplayName("분산락 + optimistic 조합은 same-key hot-key N=10/20/50에서도 외부 발송을 1회로 제한한다")
	void hotKey_withDistributedLockAndOptimistic_scalesForSameNotificationAtHigherConcurrency() throws Exception {
		for (int attemptCount : List.of(10, 20, 50)) {
			truncateTables();
			Long notificationId = createSingleNotification(
				"distributed-hot-client-" + attemptCount,
				"idem-distributed-hot-" + attemptCount
			);

			when(notificationSender.send(any())).thenAnswer(invocation -> {
				Thread.sleep(150);
				return SendResult.success();
			});

			List<Callable<Object>> tasks = new ArrayList<>();
			for (int i = 0; i < attemptCount; i++) {
				tasks.add(() -> {
					try {
						recordHandler.process(notificationId, 0);
						return "ok";
					} catch (Exception e) {
						return e;
					}
				});
			}

			List<Object> results = executeConcurrently(tasks);

			assertThat(results).allMatch(NotificationResultPredicates::isNonExceptional);
			verify(notificationSender, times(1)).send(any());

			Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
			org.mockito.Mockito.reset(notificationSender);
		}
	}

	@Test
	@DisplayName("분리된 handler/lock manager 2개는 같은 Redis 락을 공유해 multi-instance 시뮬레이션에서도 1회만 발송한다")
	void multiInstanceSimulation_withTwoHandlers_processesSameNotificationOnlyOnce() throws Exception {
		Long notificationId = createSingleNotification("multi-instance-client", "idem-multi-instance");
		CountDownLatch senderEntered = new CountDownLatch(1);

		RabbitMQRecordHandler firstHandler = new RabbitMQRecordHandler(
			notificationRepository,
			dispatchService,
			new NotificationRabbitProperties(
				"notification.work",
				"notification.work.exchange",
				"notification.wait",
				"notification.dlq",
				"notification.dlq.exchange",
				3,
				5000,
				1,
				10,
				1,
				null,
				false,
				50,
				200
			),
			new com.example.infrastructure.repository.DispatchLockManagerImpl(redissonClient)
		);
		RabbitMQRecordHandler secondHandler = new RabbitMQRecordHandler(
			notificationRepository,
			dispatchService,
			new NotificationRabbitProperties(
				"notification.work",
				"notification.work.exchange",
				"notification.wait",
				"notification.dlq",
				"notification.dlq.exchange",
				3,
				5000,
				1,
				10,
				1,
				null,
				false,
				50,
				200
			),
			new com.example.infrastructure.repository.DispatchLockManagerImpl(redissonClient)
		);

		when(notificationSender.send(any())).thenAnswer(invocation -> {
			senderEntered.countDown();
			Thread.sleep(200);
			return SendResult.success();
		});

		List<Object> results = executeConcurrently(
			() -> {
				try {
					firstHandler.process(notificationId, 0);
					return "instance-1";
				} catch (Exception e) {
					return e;
				}
			},
			() -> {
				try {
					secondHandler.process(notificationId, 0);
					return "instance-2";
				} catch (Exception e) {
					return e;
				}
			}
		);

		assertThat(senderEntered.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(results).allMatch(NotificationResultPredicates::isNonExceptional);
		verify(notificationSender, times(1)).send(any());

		Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	@DisplayName("처리 중 같은 notificationId가 redelivery되면 분산락 때문에 중복 발송 없이 스킵된다")
	void redeliveryWhileProcessing_withDistributedLock_skipsDuplicateSend() throws Exception {
		Long notificationId = createSingleNotification("redelivery-inflight-client", "idem-redelivery-inflight");
		CountDownLatch senderEntered = new CountDownLatch(1);
		CountDownLatch releaseFirstSend = new CountDownLatch(1);

		when(notificationSender.send(any())).thenAnswer(invocation -> {
			senderEntered.countDown();
			assertThat(releaseFirstSend.await(3, TimeUnit.SECONDS)).isTrue();
			return SendResult.success();
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<Object> first = executor.submit(() -> {
				try {
					recordHandler.process(notificationId, 0);
					return "first";
				} catch (Exception e) {
					return e;
				}
			});

			assertThat(senderEntered.await(3, TimeUnit.SECONDS)).isTrue();

			recordHandler.process(notificationId, 1);

			releaseFirstSend.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(3, TimeUnit.SECONDS);
		}

		verify(notificationSender, times(1)).send(any());
		Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	@DisplayName("성공 후 같은 notificationId가 redelivery되어도 terminal 상태라 재발송하지 않는다")
	void redeliveryAfterSuccess_withTerminalStatus_doesNotResend() {
		Long notificationId = createSingleNotification("redelivery-terminal-client", "idem-redelivery-terminal");

		when(notificationSender.send(any())).thenReturn(SendResult.success());

		recordHandler.process(notificationId, 0);
		recordHandler.process(notificationId, 1);

		verify(notificationSender, times(1)).send(any());
		Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	private Long createSingleNotification(String clientId, String idempotencyKey) {
		return createNotifications(
			clientId,
			idempotencyKey,
			List.of("one@test.com")
		).getFirst();
	}

	private List<Long> createNotifications(String clientId, String idempotencyKey, List<String> receivers) {
		SendCommand command = new SendCommand(
			clientId,
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			receivers,
			idempotencyKey
		);

		NotificationCommandResult result = commandService.request(command);
		NotificationGroup group = groupRepository.findByIdWithNotifications(result.groupId()).orElseThrow();
		return group.getNotifications().stream()
			.map(Notification::getId)
			.toList();
	}

	private Callable<Object> optimisticDispatchTask(Long notificationId) {
		return () -> captureResult(() -> {
			Notification detached = notificationRepository.findById(notificationId).orElseThrow();
			return dispatchService.dispatch(detached);
		});
	}

	private Callable<Object> pessimisticDispatchTask(Long notificationId) {
		return () -> captureResult(() -> transactionTemplate.execute(status -> {
			Notification locked = notificationJpaRepository.findByIdWithPessimisticLock(notificationId).orElseThrow();
			return dispatchService.dispatch(locked);
		}));
	}

	private List<Object> executeOptimisticHotKeyAttempts(List<Long> attempts) throws Exception {
		CountDownLatch loadedLatch = new CountDownLatch(attempts.size());
		List<Callable<Object>> tasks = attempts.stream()
			.<Callable<Object>>map(notificationId -> () -> captureResult(() -> {
				Notification detached = notificationRepository.findById(notificationId).orElseThrow();
				arriveAndAwait(loadedLatch);
				return dispatchService.dispatch(detached);
			}))
			.toList();
		return executeConcurrently(tasks);
	}

	private List<Long> createHotKeyAttempts(List<Long> notificationIds) {
		return List.of(
			notificationIds.get(0),
			notificationIds.get(0),
			notificationIds.get(0),
			notificationIds.get(0),
			notificationIds.get(1),
			notificationIds.get(1),
			notificationIds.get(1),
			notificationIds.get(2)
		);
	}

	private void assertNotificationsSent(List<Long> notificationIds) {
		for (Long notificationId : notificationIds) {
			Notification reloaded = notificationRepository.findById(notificationId).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
		}
	}

	private Object captureResult(Callable<Object> action) {
		try {
			return action.call();
		} catch (Exception e) {
			return e;
		}
	}

	private void arriveAndAwait(CountDownLatch latch) {
		latch.countDown();
		try {
			if (!latch.await(3, TimeUnit.SECONDS)) {
				throw new IllegalStateException("동시 시작 대기 시간이 초과되었습니다.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("동시 시작 대기 중 인터럽트가 발생했습니다.", e);
		}
	}

	private List<Object> executeConcurrently(List<Callable<Object>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch startLatch = new CountDownLatch(1);
		try {
			List<Callable<Object>> wrapped = tasks.stream()
				.<Callable<Object>>map(task -> () -> {
					startLatch.await();
					return task.call();
				})
				.toList();

			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> callable : wrapped) {
				futures.add(executor.submit(callable));
			}
			startLatch.countDown();

			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(5, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(3, TimeUnit.SECONDS);
		}
	}

	private List<Object> executeConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
		return executeConcurrently(List.of(first, second));
	}

	private void truncateTables() {
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
		jdbcTemplate.execute("TRUNCATE TABLE outbox");
		jdbcTemplate.execute("TRUNCATE TABLE notification");
		jdbcTemplate.execute("TRUNCATE TABLE notification_group");
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
	}

	private static final class NotificationResultPredicates {

		private NotificationResultPredicates() {
		}

		private static boolean isDispatchSuccess(Object value) {
			return value instanceof com.example.application.port.in.result.NotificationDispatchResult result
				&& result.isSuccess();
		}

		private static boolean isOptimisticLockFailure(Object value) {
			return value instanceof ObjectOptimisticLockingFailureException;
		}

		private static boolean isNonExceptional(Object value) {
			return !(value instanceof Exception);
		}
	}
}
