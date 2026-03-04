package com.example.infrastructure.config.rabbitmq;

import java.util.HashMap;
import java.util.Map;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.infrastructure.messaging.inbound.RabbitMQConsumer;
import com.example.infrastructure.messaging.inbound.RabbitMQRecordHandler;
import com.example.infrastructure.messaging.outbound.RabbitMQDlqPublisher;
import com.example.infrastructure.messaging.outbound.RabbitMQPublisher;
import com.example.infrastructure.messaging.outbound.RabbitMQWaitPublisher;
import com.example.infrastructure.messaging.port.DeadLetterPublisher;
import com.example.infrastructure.messaging.port.WaitPublisher;

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

	@Bean(name = RabbitBeanNames.LISTENER_CONTAINER_FACTORY)
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
		ConnectionFactory cf,
		MessageConverter mc,
		NotificationRabbitProperties p
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(cf);
		factory.setMessageConverter(mc);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(p.resolveConcurrency()); // 초기 스레드 풀 크기
		factory.setMaxConcurrentConsumers(p.resolveMaxConcurrency()); // 최대 스레드 풀 크기
		factory.setPrefetchCount(p.resolvePrefetchCount());
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
	public RabbitMQRecordHandler rabbitMQRecordHandler(
		NotificationRepository notificationRepository,
		NotificationDispatchUseCase dispatchService,
		NotificationRabbitProperties properties,
		DispatchLockManager lockManager) {
		return new RabbitMQRecordHandler(notificationRepository, dispatchService, properties, lockManager);
	}

	@Bean
	public RabbitMQConsumer rabbitMQConsumer(
		RabbitMQRecordHandler recordHandler,
		DeadLetterPublisher dlqPublisher,
		WaitPublisher waitPublisher) {
		return new RabbitMQConsumer(recordHandler, dlqPublisher, waitPublisher);
	}
}
