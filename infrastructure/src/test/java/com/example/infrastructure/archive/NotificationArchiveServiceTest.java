package com.example.infrastructure.archive;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationArchiveServiceTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Mock
	private PlatformTransactionManager transactionManager;

	private NotificationArchiveService archiveService;

	@BeforeEach
	void setUp() {
		archiveService = new NotificationArchiveService(
			jdbcTemplate,
			namedParameterJdbcTemplate,
			new ArchiveProperties(true, 1000, 7, null, null),
			new TransactionTemplate(transactionManager)
		);
	}

	@Test
	@DisplayName("다음 달 파티션이 이미 있으면 ALTER TABLE을 실행하지 않는다")
	void ensureNextMonthPartitions_doesNothingWhenPartitionExists() {
		when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq(Integer.class),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(1)
			.thenReturn(1)
			.thenReturn(1);

		archiveService.ensureNextMonthPartitions();

		verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.startsWith("ALTER TABLE"));
	}
}
