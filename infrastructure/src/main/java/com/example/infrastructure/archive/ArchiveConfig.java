package com.example.infrastructure.archive;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.out.ArchiveStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "archive.enabled", havingValue = "true")
@EnableConfigurationProperties({ArchiveProperties.class, S3ArchiveProperties.class})
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
	@ConditionalOnProperty(name = "archive.s3.enabled", havingValue = "true")
	public S3Client s3Client(S3ArchiveProperties s3Properties) {
		return S3Client.builder()
			.region(Region.of(s3Properties.region()))
			.build();
	}

	@Bean
	@ConditionalOnProperty(name = "archive.s3.enabled", havingValue = "true")
	public ArchiveStorage s3ArchiveStorage(
		JdbcTemplate jdbcTemplate,
		S3Client s3Client,
		S3ArchiveProperties s3Properties
	) {
		ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return new S3ArchiveStorage(jdbcTemplate, s3Client, s3Properties, objectMapper);
	}

	@Bean
	@ConditionalOnMissingBean(ArchiveStorage.class)
	public ArchiveStorage noOpArchiveStorage() {
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
