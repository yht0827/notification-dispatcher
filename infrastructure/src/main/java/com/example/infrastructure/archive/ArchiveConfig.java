package com.example.infrastructure.archive;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.out.ArchiveStorage;

@Configuration
@ConditionalOnProperty(name = "archive.enabled", havingValue = "true")
@EnableConfigurationProperties(ArchiveProperties.class)
public class ArchiveConfig {

	@Bean
	public NotificationArchiveService notificationArchiveService(
		JdbcTemplate jdbcTemplate,
		NamedParameterJdbcTemplate namedParameterJdbcTemplate,
		ArchiveProperties archiveProperties,
		TransactionTemplate transactionTemplate
	) {
		return new NotificationArchiveService(
			jdbcTemplate,
			namedParameterJdbcTemplate,
			archiveProperties,
			transactionTemplate
		);
	}

	@Bean
	public ArchiveStorage archiveStorage() {
		return new NoOpArchiveStorage();
	}

	@Bean
	public NotificationPartitionManager notificationPartitionManager(
		JdbcTemplate jdbcTemplate,
		ArchiveProperties archiveProperties,
		ArchiveStorage archiveStorage
	) {
		return new NotificationPartitionManager(jdbcTemplate, archiveProperties, archiveStorage);
	}

	@Bean
	public NotificationArchiveScheduler notificationArchiveScheduler(
		NotificationArchiveService notificationArchiveService,
		NotificationPartitionManager notificationPartitionManager
	) {
		return new NotificationArchiveScheduler(notificationArchiveService, notificationPartitionManager);
	}

}
