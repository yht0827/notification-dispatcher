package com.example.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import com.example.infrastructure.stream.config.NotificationStreamProperties;
import com.example.infrastructure.stream.config.StreamKeyType;
import com.example.infrastructure.stream.inbound.RedisStreamConsumer;
import com.example.infrastructure.stream.inbound.RedisStreamInitializer;
import com.example.infrastructure.stream.inbound.RedisStreamRecordHandler;
import com.example.infrastructure.stream.inbound.RedisStreamWaitScheduler;
import com.example.infrastructure.stream.outbound.RedisStreamDlqPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

@Configuration
@ConditionalOnProperty(name = NotificationConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
@Import({
	RedisStreamPublisher.class,
	RedisStreamRecordHandler.class,
	RedisStreamDlqPublisher.class,
	RedisStreamWaitPublisher.class,
	RedisStreamConsumer.class,
	RedisStreamInitializer.class,
	RedisStreamWaitScheduler.class
})
public class NotificationConfig {

	public static final String STREAM_ENABLED_PROPERTY = "notification.stream.enabled";

	@Bean
	public StreamMessageListenerContainer<String, ObjectRecord<String, NotificationStreamPayload>> streamContainer(
		RedisConnectionFactory connectionFactory,
		NotificationStreamProperties properties
	) {
		var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
			.builder()
			.pollTimeout(Duration.ofMillis(properties.pollInterval()))
			.batchSize(properties.batchSize())
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
