package com.example.infrastructure.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationRepository;
import com.example.infrastructure.config.stream.NotificationStreamConfig;
import com.example.infrastructure.config.stream.NotificationStreamProperties;
import com.example.infrastructure.stream.port.WaitPublisher;

import io.lettuce.core.api.StatefulRedisConnection;

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
		StatefulRedisConnection<String, String> lettuceStreamConnection,
		NotificationStreamProperties streamProperties,
		RecoveryProperties recoveryProperties,
		WaitPublisher waitPublisher
	) {
		return new PendingMessageReclaimer(lettuceStreamConnection, streamProperties, recoveryProperties,
			waitPublisher);
	}
}
