package com.example.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.example.infrastructure.stream.inbound.RedisStreamConsumer;
import com.example.infrastructure.stream.inbound.RedisStreamInitializer;
import com.example.infrastructure.stream.inbound.RedisStreamRecordHandler;
import com.example.infrastructure.stream.inbound.RedisStreamWaitScheduler;
import com.example.infrastructure.stream.outbound.RedisStreamDlqPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;

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
public class NotificationStreamComponentsConfig {
}
