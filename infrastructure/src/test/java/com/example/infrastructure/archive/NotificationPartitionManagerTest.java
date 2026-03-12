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

@ExtendWith(MockitoExtension.class)
class NotificationPartitionManagerTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	private NotificationPartitionManager partitionManager;

	@BeforeEach
	void setUp() {
		partitionManager = new NotificationPartitionManager(jdbcTemplate);
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

		partitionManager.ensureNextMonthPartitions();

		verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.startsWith("ALTER TABLE"));
	}
}
