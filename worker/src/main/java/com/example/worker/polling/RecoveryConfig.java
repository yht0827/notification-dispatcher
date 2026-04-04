package com.example.worker.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.worker.config.rabbitmq.RabbitMQConstants;

@Configuration
@ConditionalOnProperty(name = RabbitMQConstants.MESSAGING_ENABLED, havingValue = "true")
@ConditionalOnProperty(name = "app.consumer.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RecoveryProperties.class)
public class RecoveryConfig {

	@Bean
	public NotificationRecoveryPoller notificationRecoveryPoller(
		NotificationRepository notificationRepository,
		NotificationEventPublisher eventPublisher,
		RecoveryProperties recoveryProperties
	) {
		return new NotificationRecoveryPoller(notificationRepository, eventPublisher, recoveryProperties);
	}
}
