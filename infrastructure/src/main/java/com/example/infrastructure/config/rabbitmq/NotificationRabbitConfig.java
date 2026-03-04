package com.example.infrastructure.config.rabbitmq;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationRepository;
import com.example.infrastructure.stream.inbound.RabbitMQConsumer;
import com.example.infrastructure.stream.inbound.RabbitMQRecordHandler;
import com.example.infrastructure.stream.outbound.RabbitMQDlqPublisher;
import com.example.infrastructure.stream.outbound.RabbitMQPublisher;
import com.example.infrastructure.stream.outbound.RabbitMQWaitPublisher;
import com.example.infrastructure.stream.port.DeadLetterPublisher;
import com.example.infrastructure.stream.port.WaitPublisher;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = NotificationRabbitConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
@EnableConfigurationProperties(NotificationRabbitProperties.class)
public class NotificationRabbitConfig {

	public static final String STREAM_ENABLED_PROPERTY = "notification.stream.enabled";

	// ─── Jackson 메시지 변환기 ─────────────────
	@Bean
	public MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(jsonMessageConverter);
		return template;
	}

	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
			ConnectionFactory connectionFactory,
			MessageConverter jsonMessageConverter,
			NotificationRabbitProperties properties) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(jsonMessageConverter);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(properties.resolveConcurrency());
		factory.setMaxConcurrentConsumers(properties.resolveMaxConcurrency());
		factory.setPrefetchCount(properties.resolveMaxConcurrency());
		return factory;
	}

	// ─── Exchange 선언 ─────────────────────────
	@Bean
	public DirectExchange workExchange(NotificationRabbitProperties properties) {
		return ExchangeBuilder.directExchange(properties.workExchange()).durable(true).build();
	}

	@Bean
	public FanoutExchange dlqExchange(NotificationRabbitProperties properties) {
		return ExchangeBuilder.fanoutExchange(properties.dlqExchange()).durable(true).build();
	}

	// ─── Queue 선언 ────────────────────────────
	@Bean
	public Queue workQueue(NotificationRabbitProperties properties) {
		Map<String, Object> args = new HashMap<>();
		args.put("x-dead-letter-exchange", properties.dlqExchange());
		args.put("x-queue-type", "quorum");
		return QueueBuilder.durable(properties.workQueue()).withArguments(args).build();
	}

	@Bean
	public Queue waitQueue(NotificationRabbitProperties properties) {
		Map<String, Object> args = new HashMap<>();
		args.put("x-dead-letter-exchange", properties.workExchange());
		args.put("x-dead-letter-routing-key", properties.workQueue());
		return QueueBuilder.durable(properties.waitQueue()).withArguments(args).build();
	}

	@Bean
	public Queue dlqQueue(NotificationRabbitProperties properties) {
		Map<String, Object> args = new HashMap<>();
		args.put("x-queue-type", "quorum");
		return QueueBuilder.durable(properties.dlqQueue()).withArguments(args).build();
	}

	// ─── Binding ───────────────────────────────
	@Bean
	public Binding workBinding(Queue workQueue, DirectExchange workExchange, NotificationRabbitProperties properties) {
		return BindingBuilder.bind(workQueue).to(workExchange).with(properties.workQueue());
	}

	@Bean
	public Binding dlqBinding(Queue dlqQueue, FanoutExchange dlqExchange) {
		return BindingBuilder.bind(dlqQueue).to(dlqExchange);
	}

	// ─── 발행자 ────────────────────────────────
	@Bean
	public NotificationEventPublisher rabbitMQPublisher(RabbitTemplate rabbitTemplate, NotificationRabbitProperties properties) {
		return new RabbitMQPublisher(rabbitTemplate, properties);
	}

	@Bean
	public WaitPublisher rabbitMQWaitPublisher(RabbitTemplate rabbitTemplate, NotificationRabbitProperties properties) {
		return new RabbitMQWaitPublisher(rabbitTemplate, properties);
	}

	@Bean
	public DeadLetterPublisher rabbitMQDlqPublisher(RabbitTemplate rabbitTemplate, NotificationRabbitProperties properties) {
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
			WaitPublisher waitPublisher,
			NotificationRabbitProperties properties) {
		return new RabbitMQConsumer(recordHandler, dlqPublisher, waitPublisher, properties);
	}
}
