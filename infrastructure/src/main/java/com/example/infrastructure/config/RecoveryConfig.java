package com.example.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationRepository;
import com.example.infrastructure.recovery.NotificationRecoveryPoller;
import com.example.infrastructure.recovery.PendingMessageReclaimer;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;

@Configuration
@ConditionalOnProperty(name = NotificationStreamConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
public class RecoveryConfig {

	@Bean
	public NotificationRecoveryPoller notificationRecoveryPoller(
		NotificationRepository notificationRepository,
		NotificationEventPublisher streamPublisher,
		RecoveryProperties recoveryProperties
	) {
		return new NotificationRecoveryPoller(notificationRepository, streamPublisher, recoveryProperties);
	}

	@Bean
	public PendingMessageReclaimer pendingMessageReclaimer(
		StringRedisTemplate redisTemplate,
		NotificationStreamProperties streamProperties,
		RecoveryProperties recoveryProperties,
		RedisStreamWaitPublisher waitPublisher
	) {
		return new PendingMessageReclaimer(redisTemplate, streamProperties, recoveryProperties, waitPublisher);
	}
}
