package com.example.infrastructure.config.stream;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationRepository;
import com.example.infrastructure.stream.StreamKeyType;
import com.example.infrastructure.stream.inbound.RedisStreamConsumer;
import com.example.infrastructure.stream.inbound.RedisStreamInitializer;
import com.example.infrastructure.stream.inbound.RedisStreamRecordHandler;
import com.example.infrastructure.stream.inbound.RedisStreamWaitScheduler;
import com.example.infrastructure.stream.outbound.RedisStreamDlqPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;
import com.example.infrastructure.stream.port.DeadLetterPublisher;
import com.example.infrastructure.stream.port.WaitPublisher;

@Configuration
@ConditionalOnProperty(name = NotificationStreamConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
public class NotificationStreamConfig {

	public static final String STREAM_ENABLED_PROPERTY = "notification.stream.enabled";

	// Publisher
	@Bean
	public NotificationEventPublisher redisStreamPublisher(
		StringRedisTemplate redisTemplate,
		NotificationStreamProperties properties
	) {
		return new RedisStreamPublisher(redisTemplate, properties);
	}

	@Bean
	public DeadLetterPublisher deadLetterPublisher(
		StringRedisTemplate redisTemplate,
		NotificationStreamProperties properties
	) {
		return new RedisStreamDlqPublisher(redisTemplate, properties);
	}

	@Bean
	public WaitPublisher waitPublisher(
		StringRedisTemplate redisTemplate,
		NotificationStreamProperties properties
	) {
		return new RedisStreamWaitPublisher(redisTemplate, properties);
	}

	// Consumer
	@Bean
	public RedisStreamRecordHandler redisStreamRecordHandler(
		NotificationRepository notificationRepository,
		NotificationDispatchUseCase dispatchService,
		NotificationStreamProperties properties,
		DispatchLockManager lockManager
	) {
		return new RedisStreamRecordHandler(notificationRepository, dispatchService, properties, lockManager);
	}

	@Bean
	public RedisStreamConsumer redisStreamConsumer(
		StringRedisTemplate redisTemplate,
		RedisStreamRecordHandler recordHandler,
		DeadLetterPublisher dlqPublisher,
		WaitPublisher waitPublisher,
		NotificationStreamProperties properties
	) {
		return new RedisStreamConsumer(redisTemplate, recordHandler, dlqPublisher, waitPublisher, properties);
	}

	@Bean
	public RedisStreamInitializer redisStreamInitializer(
		StringRedisTemplate redisTemplate,
		WaitPublisher waitPublisher,
		NotificationStreamProperties properties
	) {
		return new RedisStreamInitializer(redisTemplate, waitPublisher, properties);
	}

	@Bean
	public RedisStreamWaitScheduler redisStreamWaitScheduler(
		StringRedisTemplate redisTemplate,
		NotificationStreamProperties properties
	) {
		return new RedisStreamWaitScheduler(redisTemplate, properties);
	}

	// Listener

	@Bean
	public Executor streamConsumerTaskExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean
	public StreamMessageListenerContainer<String, ObjectRecord<String, NotificationStreamPayload>> streamContainer(
		RedisConnectionFactory connectionFactory,
		NotificationStreamProperties properties,
		Executor streamConsumerTaskExecutor
	) {
		var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
			.builder()
			.pollTimeout(Duration.ofMillis(properties.pollInterval()))
			.batchSize(properties.batchSize())
			.executor(streamConsumerTaskExecutor)
			.targetType(NotificationStreamPayload.class)
			.build();

		return StreamMessageListenerContainer.create(connectionFactory, options);
	}

	@Bean
	public Subscription streamSubscription(
		StreamMessageListenerContainer<String, ObjectRecord<String, NotificationStreamPayload>> container,
		RedisStreamConsumer consumer,
		NotificationStreamProperties properties
	) {
		Subscription subscription = container.receive(
			Consumer.from(properties.consumerGroup(), properties.consumerName()),
			StreamOffset.create(properties.resolveKey(StreamKeyType.WORK), ReadOffset.lastConsumed()),
			consumer
		);
		container.start();
		return subscription;
	}

}
