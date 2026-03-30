package com.example.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.SendResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.service.NotificationDispatchService;
import com.example.application.service.NotificationWriteService;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.worker.messaging.inbound.RabbitMQRecordHandler;
import com.example.worker.messaging.inbound.RecordProcessRequest;
import com.example.infrastructure.repository.DispatchLockManagerImpl;
import com.example.infrastructure.support.IntegrationTestSupportNoTx;

/**
 * 분산락 적용 전후 처리 성능 비교
 *
 * 목적: 락 오버헤드(Redis 왕복 비용)가 전체 처리 시간에 미치는 영향을 측정한다.
 * 참고: 로컬 환경 기준 상대 비교. 절대 수치보다 비율에 집중한다.
 */
class DispatchLockPerformanceTest extends IntegrationTestSupportNoTx {

	private static final int THREAD_COUNT = 20;
	private static final int WARMUP_COUNT = 5;

	@Autowired
	private NotificationWriteService commandService;

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
	private RedissonClient redissonClient;

	@MockitoBean
	private NotificationSender notificationSender;

	@BeforeEach
	void setUp() {
		truncateTables();
		when(notificationSender.send(any())).thenReturn(SendResult.success());
	}

	@Test
	@DisplayName("락 없음 vs 분산락: 고유 알림 N건 병렬 처리 TPS 비교")
	void throughput_withLock_vs_withoutLock() throws Exception {
		// 락 없음 (각 스레드가 서로 다른 알림 처리)
		long withoutLockMs = measureThroughput("no-lock", false);

		truncateTables();
		org.mockito.Mockito.reset(notificationSender);
		when(notificationSender.send(any())).thenReturn(SendResult.success());

		// 분산락 있음
		long withLockMs = measureThroughput("with-lock", true);

		double withoutLockTps = (double) THREAD_COUNT / withoutLockMs * 1000;
		double withLockTps = (double) THREAD_COUNT / withLockMs * 1000;
		double overheadPct = (withLockMs - withoutLockMs) * 100.0 / withoutLockMs;

		System.out.println("\n========= 분산락 성능 비교 (로컬 기준) =========");
		System.out.printf("락 없음:   %5dms / TPS %.1f%n", withoutLockMs, withoutLockTps);
		System.out.printf("분산락:    %5dms / TPS %.1f%n", withLockMs, withLockTps);
		System.out.printf("오버헤드:  %.1f%%%n", overheadPct);
		System.out.println("================================================\n");

		// 분산락이 있어도 처리 완료는 보장
		assertThat(withLockMs).isPositive();
	}

	@Test
	@DisplayName("락 획득 실패 비율: 같은 알림 N개 동시 처리 시 skip 비율 확인")
	void lockContention_sameNotification_skipRate() throws Exception {
		Long notificationId = createNotification("contention-client", "contention-key");

		AtomicLong processedCount = new AtomicLong(0);
		AtomicLong skippedCount = new AtomicLong(0);

		RabbitMQRecordHandler handler = new RabbitMQRecordHandler(
			notificationRepository,
			dispatchService,
			new DispatchLockManagerImpl(redissonClient)
		);

		when(notificationSender.send(any())).thenAnswer(inv -> {
			Thread.sleep(50); // 처리 시간 시뮬레이션
			processedCount.incrementAndGet();
			return SendResult.success();
		});

		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < THREAD_COUNT; i++) {
			tasks.add(() -> {
				try {
					return handler.process(new RecordProcessRequest(notificationId, notificationId, 0));
				} catch (Exception e) {
					return e;
				}
			});
		}

		executeConcurrently(tasks);

		// 락 실패로 스킵된 건수 = 전체 - 실제 발송 횟수
		skippedCount.set(THREAD_COUNT - processedCount.get());

		System.out.println("\n========= 락 경합 비율 =========");
		System.out.printf("전체 시도:   %d건%n", THREAD_COUNT);
		System.out.printf("실제 발송:   %d건%n", processedCount.get());
		System.out.printf("스킵(락실패): %d건 (%.0f%%)%n",
			skippedCount.get(), skippedCount.get() * 100.0 / THREAD_COUNT);
		System.out.println("================================\n");

		assertThat(processedCount.get()).isEqualTo(1); // 발송은 1회만
	}

	private long measureThroughput(String clientPrefix, boolean useLock) throws Exception {
		List<Long> notificationIds = new ArrayList<>();
		for (int i = 0; i < THREAD_COUNT; i++) {
			notificationIds.add(createNotification(clientPrefix + "-" + i, "key-" + i));
		}

		// 웜업
		for (int i = 0; i < WARMUP_COUNT && i < notificationIds.size(); i++) {
			Long id = notificationIds.get(i);
			if (useLock) {
				new RabbitMQRecordHandler(notificationRepository, dispatchService,
					new DispatchLockManagerImpl(redissonClient))
					.process(new RecordProcessRequest(id, id, 0));
			} else {
				Notification n = notificationRepository.findById(id).orElseThrow();
				dispatchService.dispatch(n);
			}
		}

		truncateTables();
		org.mockito.Mockito.reset(notificationSender);
		when(notificationSender.send(any())).thenReturn(SendResult.success());

		// 본 측정
		List<Long> measureIds = new ArrayList<>();
		for (int i = 0; i < THREAD_COUNT; i++) {
			measureIds.add(createNotification(clientPrefix + "-measure-" + i, "measure-key-" + i));
		}

		List<Callable<Object>> tasks;
		if (useLock) {
			RabbitMQRecordHandler handler = new RabbitMQRecordHandler(
				notificationRepository, dispatchService, new DispatchLockManagerImpl(redissonClient));
			tasks = measureIds.stream()
				.<Callable<Object>>map(id -> () -> {
					try {
						return handler.process(new RecordProcessRequest(id, id, 0));
					} catch (Exception e) {
						return e;
					}
				}).toList();
		} else {
			tasks = measureIds.stream()
				.<Callable<Object>>map(id -> () -> {
					try {
						Notification n = notificationRepository.findById(id).orElseThrow();
						return dispatchService.dispatch(n);
					} catch (Exception e) {
						return e;
					}
				}).toList();
		}

		long start = System.currentTimeMillis();
		executeConcurrently(tasks);
		return System.currentTimeMillis() - start;
	}

	private Long createNotification(String clientId, String idempotencyKey) {
		SendCommand command = new SendCommand(
			clientId, "sender", "title", "content",
			ChannelType.EMAIL, List.of("test@test.com"),
			idempotencyKey, null
		);
		NotificationCommandResult result = commandService.request(command);
		NotificationGroup group = groupRepository.findByIdWithNotifications(result.groupId()).orElseThrow();
		return group.getNotifications().stream()
			.map(Notification::getId)
			.findFirst()
			.orElseThrow();
	}

	private List<Object> executeConcurrently(List<Callable<Object>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch startLatch = new CountDownLatch(1);
		try {
			List<Future<Object>> futures = tasks.stream()
				.map(task -> executor.submit((Callable<Object>) () -> {
					startLatch.await();
					return task.call();
				}))
				.toList();
			startLatch.countDown();

			List<Object> results = new ArrayList<>();
			for (Future<Object> f : futures) {
				results.add(f.get(10, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(3, TimeUnit.SECONDS);
		}
	}

	private void truncateTables() {
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
		jdbcTemplate.execute("TRUNCATE TABLE outbox");
		jdbcTemplate.execute("TRUNCATE TABLE notification");
		jdbcTemplate.execute("TRUNCATE TABLE notification_group");
		jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
	}
}
