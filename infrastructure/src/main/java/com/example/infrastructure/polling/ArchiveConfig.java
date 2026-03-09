package com.example.infrastructure.polling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.out.cache.NotificationGroupDetailCacheRepository;

@Configuration
@ConditionalOnProperty(name = "archive.enabled", havingValue = "true")
@EnableConfigurationProperties(ArchiveProperties.class)
public class ArchiveConfig {

	@Bean
	public NotificationArchiveService notificationArchiveService(
		JdbcTemplate jdbcTemplate,
		NamedParameterJdbcTemplate namedParameterJdbcTemplate,
		ArchiveProperties archiveProperties,
		TransactionTemplate transactionTemplate,
		NotificationGroupDetailCacheRepository groupDetailCacheRepository
	) {
		return new NotificationArchiveService(
			jdbcTemplate,
			namedParameterJdbcTemplate,
			archiveProperties,
			transactionTemplate,
			groupDetailCacheRepository
		);
	}

	@Bean
	public NotificationArchiveScheduler notificationArchiveScheduler(
		NotificationArchiveService notificationArchiveService
	) {
		return new NotificationArchiveScheduler(notificationArchiveService);
	}

	@Bean
	@ConditionalOnProperty(name = "archive.run-on-startup", havingValue = "true")
	public NotificationArchiveStartupRunner notificationArchiveStartupRunner(
		NotificationArchiveService notificationArchiveService
	) {
		return new NotificationArchiveStartupRunner(notificationArchiveService);
	}
}
