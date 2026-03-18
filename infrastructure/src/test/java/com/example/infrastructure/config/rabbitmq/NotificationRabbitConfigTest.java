package com.example.infrastructure.config.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationRabbitConfigTest {

	private final NotificationRabbitConfig config = new NotificationRabbitConfig();

	@Test
	@DisplayName("listenerVirtualThreads 미지정 시 spring virtual thread 설정을 상속한다")
	void rabbitListenerTaskExecutor_inheritsAppVirtualThreads() throws Exception {
		Executor executor = config.rabbitListenerTaskExecutor(createProperties(null), true);

		assertThat(isVirtualThread(executor)).isTrue();
	}

	@Test
	@DisplayName("listenerVirtualThreads 미지정이고 앱 virtual thread가 꺼져 있으면 플랫폼 스레드를 사용한다")
	void rabbitListenerTaskExecutor_usesPlatformThreadsWhenAppVirtualThreadsDisabled() throws Exception {
		Executor executor = config.rabbitListenerTaskExecutor(createProperties(null), false);

		try {
			assertThat(isVirtualThread(executor)).isFalse();
		} finally {
			destroyIfNeeded(executor);
		}
	}

	@Test
	@DisplayName("listenerVirtualThreads=true 이면 앱 전역 설정과 무관하게 virtual thread를 사용한다")
	void rabbitListenerTaskExecutor_allowsExplicitOverride() throws Exception {
		Executor executor = config.rabbitListenerTaskExecutor(createProperties(Boolean.TRUE), false);

		assertThat(isVirtualThread(executor)).isTrue();
	}

	@Test
	@DisplayName("single listener container factory는 주입된 executor를 그대로 사용한다")
	void rabbitSingleListenerContainerFactory_usesInjectedExecutor() {
		Executor executor = config.rabbitListenerTaskExecutor(createProperties(Boolean.FALSE), false);
		SimpleRabbitListenerContainerFactory factory = config.rabbitSingleListenerContainerFactory(
			org.mockito.Mockito.mock(ConnectionFactory.class),
			new Jackson2JsonMessageConverter(),
			createProperties(Boolean.FALSE),
			executor
		);

		try {
			assertThat(ReflectionTestUtils.getField(factory, "taskExecutor")).isSameAs(executor);
		} finally {
			destroyIfNeeded(executor);
		}
	}

	private NotificationRabbitProperties createProperties(Boolean listenerVirtualThreads) {
		return new NotificationRabbitProperties(
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
			listenerVirtualThreads,
			0.0d
		);
	}

	private boolean isVirtualThread(Executor executor) throws InterruptedException {
		AtomicBoolean isVirtualThread = new AtomicBoolean(false);
		CountDownLatch latch = new CountDownLatch(1);

		executor.execute(() -> {
			isVirtualThread.set(Thread.currentThread().isVirtual());
			latch.countDown();
		});

		assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
		return isVirtualThread.get();
	}

	private void destroyIfNeeded(Executor executor) {
		if (executor instanceof ThreadPoolTaskExecutor threadPoolTaskExecutor) {
			threadPoolTaskExecutor.destroy();
		}
	}
}
