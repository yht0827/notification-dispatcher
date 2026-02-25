package com.example.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import com.example.infrastructure.stream.inbound.RedisStreamConsumer;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

@Configuration
@ConditionalOnProperty(name = NotificationConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
public class NotificationStreamListenerConfig {

	private final NotificationStreamProperties properties;
	private final RedisStreamConsumer consumer;

	public NotificationStreamListenerConfig(NotificationStreamProperties properties, RedisStreamConsumer consumer) {
		this.properties = properties;
		this.consumer = consumer;
	}

	@Bean
	public StreamMessageListenerContainer<String, ObjectRecord<String, NotificationStreamPayload>> streamContainer(
		RedisConnectionFactory connectionFactory
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
		StreamMessageListenerContainer<String, ObjectRecord<String, NotificationStreamPayload>> container
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
