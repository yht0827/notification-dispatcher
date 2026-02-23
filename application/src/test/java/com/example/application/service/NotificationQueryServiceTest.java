package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.in.NotificationGroupSlice;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.application.port.out.NotificationRepository;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

	@Mock
	private NotificationGroupRepository groupRepository;

	@Mock
	private NotificationRepository notificationRepository;

	@InjectMocks
	private NotificationQueryService queryService;

	@Test
	@DisplayName("알림 묶음 조회 시 요청 크기보다 하나 더 조회해 hasNext와 nextCursorId를 계산한다")
	void getRecentGroups_returnsSliceWithCursor() {
		// given
		NotificationGroup first = org.mockito.Mockito.mock(NotificationGroup.class);
		NotificationGroup second = org.mockito.Mockito.mock(NotificationGroup.class);
		NotificationGroup third = org.mockito.Mockito.mock(NotificationGroup.class);
		when(first.getId()).thenReturn(300L);
		when(second.getId()).thenReturn(200L);

		when(groupRepository.findRecentByCursor(null, 3)).thenReturn(List.of(first, second, third));

		// when
		NotificationGroupSlice slice = queryService.getRecentGroups(null, 2);

		// then
		assertThat(slice.items()).hasSize(2);
		assertThat(slice.items().get(0).getId()).isEqualTo(300L);
		assertThat(slice.items().get(1).getId()).isEqualTo(200L);
		assertThat(slice.hasNext()).isTrue();
		assertThat(slice.nextCursorId()).isEqualTo(200L);
		verify(groupRepository).findRecentByCursor(null, 3);
	}

	@Test
	@DisplayName("추가 데이터가 없으면 hasNext는 false이고 nextCursorId는 null이다")
	void getRecentGroups_returnsSliceWithoutNextCursor() {
		// given
		NotificationGroup only = org.mockito.Mockito.mock(NotificationGroup.class);
		when(groupRepository.findRecentByCursor(50L, 3)).thenReturn(List.of(only));

		// when
		NotificationGroupSlice slice = queryService.getRecentGroups(50L, 2);

		// then
		assertThat(slice.items()).hasSize(1);
		assertThat(slice.hasNext()).isFalse();
		assertThat(slice.nextCursorId()).isNull();
		verify(groupRepository).findRecentByCursor(50L, 3);
	}
}
