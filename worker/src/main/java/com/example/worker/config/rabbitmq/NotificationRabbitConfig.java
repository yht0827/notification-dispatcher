package com.example.worker.config.rabbitmq;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.worker.messaging.inbound.RabbitMQRecordHandler;
import com.example.worker.messaging.outbound.DeadLetterPublisher;
import com.example.worker.messaging.outbound.RabbitMQDlqPublisher;
import com.example.worker.messaging.outbound.RabbitMQPublisher;
import com.example.worker.messaging.outbound.RabbitMQWaitPublisher;
import com.example.worker.messaging.outbound.WaitPublisher;

@Configuration
@ConditionalOnProperty(name = RabbitPropertyKeys.MESSAGING_ENABLED, havingValue = "true")
@EnableConfigurationProperties(NotificationRabbitProperties.class)
public class NotificationRabbitConfig {

	private static final String ARG_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
	private static final String ARG_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";
	private static final String ARG_QUEUE_TYPE = "x-queue-type";
	private static final String QUEUE_TYPE_QUORUM = "quorum";

	@Bean
	public MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
		RabbitTemplate template = new RabbitTemplate(cf);
		template.setMessageConverter(mc);
		return template;
	}

	@Bean(name = RabbitBeanNames.SINGLE_LISTENER_CONTAINER_FACTORY)
	@ConditionalOnProperty(name = "app.consumer.enabled", havingValue = "true", matchIfMissing = true)
	public SimpleRabbitListenerContainerFactory rabbitSingleListenerContainerFactory(
		ConnectionFactory cf,
		MessageConverter mc,
		NotificationRabbitProperties p,
		@Qualifier(RabbitBeanNames.LISTENER_TASK_EXECUTOR) Executor listenerTaskExecutor
	) {
		return createBaseListenerContainerFactory(cf, mc, p, listenerTaskExecutor);
	}

	@Bean(name = RabbitBeanNames.LISTENER_TASK_EXECUTOR)
	@ConditionalOnProperty(name = "app.consumer.enabled", havingValue = "true", matchIfMissing = true)
	public Executor rabbitListenerTaskExecutor(
		NotificationRabbitProperties properties,
		@Value("${spring.threads.virtual.enabled:false}") boolean appVirtualThreadsEnabled
	) {
		if (properties.resolveListenerVirtualThreads(appVirtualThreadsEnabled)) {
			SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("rabbit-listener-vt-");
			executor.setVirtualThreads(true);
			return executor;
		}

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("rabbit-listener-");
		executor.setCorePoolSize(properties.resolveConcurrency());
		executor.setMaxPoolSize(properties.resolveMaxConcurrency());
		executor.setQueueCapacity(0);
		executor.setAllowCoreThreadTimeOut(true);
		executor.initialize();
		return executor;
	}

	private SimpleRabbitListenerContainerFactory createBaseListenerContainerFactory(
		ConnectionFactory connectionFactory,
		MessageConverter messageConverter,
		NotificationRabbitProperties properties,
		Executor listenerTaskExecutor
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(messageConverter);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(properties.resolveConcurrency());
		factory.setMaxConcurrentConsumers(properties.resolveMaxConcurrency());
		factory.setPrefetchCount(properties.resolvePrefetchCount());
		factory.setTaskExecutor(listenerTaskExecutor);
		return factory;
	}

	// 기본 작업 Exchanges
	@Bean
	public DirectExchange workExchange(NotificationRabbitProperties p) {
		return ExchangeBuilder
			.directExchange(p.workExchange())
			.durable(true)
			.build();
	}

	// 재시도 대기 Exchanges
	@Bean
	public DirectExchange waitExchange(NotificationRabbitProperties p) {
		return ExchangeBuilder
			.directExchange(p.waitExchange())
			.durable(true)
			.build();
	}

	// Dead Letter Exchanges
	@Bean
	public FanoutExchange dlqExchange(NotificationRabbitProperties p) {
		return ExchangeBuilder
			.fanoutExchange(p.dlqExchange())
			.durable(true)
			.build();
	}

	// Queues
	@Bean
	public Queue workQueue(NotificationRabbitProperties p) {
		return QueueBuilder
			.durable(p.workQueue())
			.withArguments(workQueueArguments(p))
			.build();
	}

	@Bean
	public Queue waitQueue(NotificationRabbitProperties p) {
		return QueueBuilder
			.durable(p.waitQueue())
			.withArguments(waitQueueArguments(p))
			.build();
	}

	@Bean
	public Queue dlqQueue(NotificationRabbitProperties p) {
		return QueueBuilder
			.durable(p.dlqQueue())
			.withArguments(dlqQueueArguments())
			.build();
	}

	// workQueue ← workExchange (key: notification.work)
	@Bean
	public Binding workBinding(
		Queue workQueue,
		DirectExchange workExchange,
		NotificationRabbitProperties p
	) {
		return BindingBuilder
			.bind(workQueue)
			.to(workExchange)
			.with(p.workRoutingKey());
	}

	// waitQueue ← waitExchange (key: notification.wait)
	@Bean
	public Binding waitBinding(
		Queue waitQueue,
		DirectExchange waitExchange,
		NotificationRabbitProperties p
	) {
		return BindingBuilder
			.bind(waitQueue)
			.to(waitExchange)
			.with(p.waitRoutingKey());
	}

	// dlqQueue ← dlqExchange (fanout)
	@Bean
	public Binding dlqBinding(
		Queue dlqQueue,
		FanoutExchange dlqExchange
	) {
		return BindingBuilder
			.bind(dlqQueue)
			.to(dlqExchange);
	}

	private Map<String, Object> workQueueArguments(NotificationRabbitProperties properties) {
		Map<String, Object> args = quorumQueueArguments();
		args.put(ARG_DEAD_LETTER_EXCHANGE, properties.dlqExchange());
		return args;
	}

	private Map<String, Object> waitQueueArguments(NotificationRabbitProperties properties) {
		Map<String, Object> args = new HashMap<>();
		args.put(ARG_DEAD_LETTER_EXCHANGE, properties.workExchange());
		args.put(ARG_DEAD_LETTER_ROUTING_KEY, properties.workRoutingKey());
		return args;
	}

	private Map<String, Object> dlqQueueArguments() {
		return quorumQueueArguments();
	}

	private Map<String, Object> quorumQueueArguments() {
		Map<String, Object> args = new HashMap<>();
		args.put(ARG_QUEUE_TYPE, QUEUE_TYPE_QUORUM);
		return args;
	}

	// ─── 발행자 ────────────────────────────────
	@Bean
	public NotificationEventPublisher rabbitMQPublisher(RabbitTemplate rabbitTemplate,
		NotificationRabbitProperties properties) {
		return new RabbitMQPublisher(rabbitTemplate, properties);
	}

	@Bean
	public WaitPublisher rabbitMQWaitPublisher(RabbitTemplate rabbitTemplate, NotificationRabbitProperties properties) {
		return new RabbitMQWaitPublisher(rabbitTemplate, properties);
	}

	@Bean
	public DeadLetterPublisher rabbitMQDlqPublisher(RabbitTemplate rabbitTemplate,
		NotificationRabbitProperties properties) {
		return new RabbitMQDlqPublisher(rabbitTemplate, properties);
	}

	// ─── 핸들러 / 컨슈머 ───────────────────────
	@Bean
	@ConditionalOnProperty(name = "app.consumer.enabled", havingValue = "true", matchIfMissing = true)
	public RabbitMQRecordHandler rabbitMQRecordHandler(
		NotificationRepository notificationRepository,
		NotificationDispatchUseCase dispatchService,
		DispatchLockManager lockManager) {
		return new RabbitMQRecordHandler(notificationRepository, dispatchService, lockManager);
	}

}
