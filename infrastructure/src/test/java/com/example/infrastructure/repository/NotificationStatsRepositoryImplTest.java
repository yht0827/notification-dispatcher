package com.example.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class NotificationStatsRepositoryImplTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	private NotificationStatsRepositoryImpl repository;

	@BeforeEach
	void setUp() {
		repository = new NotificationStatsRepositoryImpl(jdbcTemplate);
	}

	@Test
	@DisplayName("countByStatus는 전체 notification 상태별 건수를 반환한다")
	void countByStatus_returnsStatusCounts() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.next()).thenReturn(true, true, false);
		when(rs.getString(1)).thenReturn("SENT", "PENDING");
		when(rs.getLong(2)).thenReturn(10L, 5L);

		doAnswer(invocation -> {
			ResultSetExtractor<Map<String, Long>> extractor = invocation.getArgument(1);
			return extractor.extractData(rs);
		}).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class));

		Map<String, Long> result = repository.countByStatus();

		assertThat(result).containsEntry("SENT", 10L).containsEntry("PENDING", 5L);
	}

	@Test
	@DisplayName("countByStatusAndClientId는 특정 클라이언트의 상태별 건수를 반환한다")
	void countByStatusAndClientId_returnsStatusCountsForClient() throws SQLException {
		ResultSet rs = mock(ResultSet.class);
		when(rs.next()).thenReturn(true, false);
		when(rs.getString(1)).thenReturn("SENT");
		when(rs.getLong(2)).thenReturn(3L);

		doAnswer(invocation -> {
			ResultSetExtractor<Map<String, Long>> extractor = invocation.getArgument(1);
			return extractor.extractData(rs);
		}).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class), eq("client-1"));

		Map<String, Long> result = repository.countByStatusAndClientId("client-1");

		assertThat(result).containsEntry("SENT", 3L);
	}
}
