package com.example.infrastructure.config.stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationRepository;
import com.example.infrastructure.polling.NotificationRecoveryPoller;
import com.example.infrastructure.polling.PendingMessageReclaimer;
import com.example.infrastructure.stream.port.WaitPublisher;

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
		WaitPublisher waitPublisher
	) {
		return new PendingMessageReclaimer(redisTemplate, streamProperties, recoveryProperties, waitPublisher);
	}
}
