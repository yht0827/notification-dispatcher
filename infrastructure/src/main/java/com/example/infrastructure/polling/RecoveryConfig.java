package com.example.infrastructure.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationRepository;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitConfig;

@Configuration
@ConditionalOnProperty(name = NotificationRabbitConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
@EnableConfigurationProperties(RecoveryProperties.class)
public class RecoveryConfig {

	@Bean
	public NotificationRecoveryPoller notificationRecoveryPoller(
		NotificationRepository notificationRepository,
		NotificationEventPublisher streamPublisher,
		RecoveryProperties recoveryProperties
	) {
		return new NotificationRecoveryPoller(notificationRepository, streamPublisher, recoveryProperties);
	}
}
