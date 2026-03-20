package com.example.application.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.application.port.in.result.NotificationCommandResult;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStats;

class NotificationCommandResultMapperTest {

	private final NotificationCommandResultMapper mapper = new NotificationCommandResultMapper();

	@Test
	@DisplayName("NotificationGroup을 NotificationCommandResult로 변환한다")
	void toResult() {
		NotificationGroup group = mock(NotificationGroup.class);
		when(group.getId()).thenReturn(10L);
		when(group.getStats()).thenReturn(new NotificationStats(3, 0, 0));

		NotificationCommandResult result = mapper.toResult(group);

		assertThat(result.groupId()).isEqualTo(10L);
		assertThat(result.totalCount()).isEqualTo(3);
	}
}
