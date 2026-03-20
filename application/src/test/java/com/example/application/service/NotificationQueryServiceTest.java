package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.service.mapper.NotificationResultMapper;
import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.application.port.in.result.NotificationUnreadCountResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStats;
import com.example.domain.notification.NotificationStatus;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

	@Mock
	private NotificationGroupRepository groupRepository;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationReadStatusRepository notificationReadStatusRepository;

	@Spy
	private NotificationResultMapper mapper;

	@InjectMocks
	private NotificationQueryService queryService;

	@Test
	@DisplayName("요청자별 조회 시 hasNext가 true이면 nextCursorId가 설정된다")
	void getGroupsByClientId_returnsSliceWithCursor() {
		// given
		NotificationGroup first = org.mockito.Mockito.mock(NotificationGroup.class);
		NotificationGroup second = org.mockito.Mockito.mock(NotificationGroup.class);
		NotificationGroup third = org.mockito.Mockito.mock(NotificationGroup.class);
		NotificationStats emptyStats = new NotificationStats(0, 0, 0);
		when(first.getId()).thenReturn(300L);
		when(first.getStats()).thenReturn(emptyStats);
		when(second.getId()).thenReturn(200L);
		when(second.getStats()).thenReturn(emptyStats);
		when(third.getStats()).thenReturn(emptyStats);
		when(groupRepository.findByClientIdWithCursor(
			org.mockito.ArgumentMatchers.eq("order-service"),
			any(java.time.LocalDateTime.class),
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.eq(3)
		)).thenReturn(List.of(first, second, third));

		// when
		CursorSlice<NotificationGroupResult> slice =
			queryService.getGroupsByClientId("order-service", null, 2, null);

		// then
		assertThat(slice.items()).hasSize(2);
		assertThat(slice.items().get(0).id()).isEqualTo(300L);
		assertThat(slice.items().get(1).id()).isEqualTo(200L);
		assertThat(slice.hasNext()).isTrue();
		assertThat(slice.nextCursorId()).isEqualTo(200L);
	}

	@Test
	@DisplayName("요청자별 조회 시 추가 데이터가 없으면 hasNext는 false이고 nextCursorId는 null이다")
	void getGroupsByClientId_returnsSliceWithoutNextCursor() {
		// given
		NotificationGroup only = org.mockito.Mockito.mock(NotificationGroup.class);
		when(only.getStats()).thenReturn(new NotificationStats(0, 0, 0));
		when(groupRepository.findByClientIdWithCursor(
			org.mockito.ArgumentMatchers.eq("order-service"),
			any(java.time.LocalDateTime.class),
			org.mockito.ArgumentMatchers.eq(50L),
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.eq(3)
		)).thenReturn(List.of(only));

		// when
		CursorSlice<NotificationGroupResult> slice =
			queryService.getGroupsByClientId("order-service", 50L, 2, null);

		// then
		assertThat(slice.items()).hasSize(1);
		assertThat(slice.hasNext()).isFalse();
		assertThat(slice.nextCursorId()).isNull();
	}

	@Test
	@DisplayName("그룹 단건 조회 시 결과를 매핑해 반환한다")
	void getGroup_returnsMappedGroup() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		when(group.getId()).thenReturn(10L);
		when(group.getClientId()).thenReturn("client-1");
		when(group.getSender()).thenReturn("sender");
		when(group.getTitle()).thenReturn("title");
		when(group.getGroupType()).thenReturn(GroupType.BULK);
		when(group.getChannelType()).thenReturn(ChannelType.EMAIL);
		when(group.getStats()).thenReturn(new NotificationStats(3, 2, 0));
		when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

		Optional<NotificationGroupResult> result = queryService.getGroup(10L);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().id()).isEqualTo(10L);
		verify(groupRepository).findById(10L);
	}

	@Test
	@DisplayName("그룹 상세 조회 시 하위 알림까지 매핑해 반환한다")
	void getGroupDetail_returnsMappedDetail() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		Notification notification = org.mockito.Mockito.mock(Notification.class);
		when(group.getId()).thenReturn(10L);
		when(group.getClientId()).thenReturn("client-1");
		when(group.getSender()).thenReturn("sender");
		when(group.getTitle()).thenReturn("title");
		when(group.getContent()).thenReturn("content");
		when(group.getGroupType()).thenReturn(GroupType.BULK);
		when(group.getChannelType()).thenReturn(ChannelType.EMAIL);
		when(group.getStats()).thenReturn(new NotificationStats(1, 0, 0));
		when(group.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(1));
		when(group.getNotifications()).thenReturn(List.of(notification));
		when(notification.getId()).thenReturn(101L);
		when(notification.getReceiver()).thenReturn("user@example.com");
		when(notification.getStatus()).thenReturn(NotificationStatus.PENDING);
		when(groupRepository.findByIdWithNotifications(10L)).thenReturn(Optional.of(group));
		when(notificationReadStatusRepository.findReadAtByNotificationIds(List.of(101L)))
			.thenReturn(java.util.Map.of(101L, LocalDateTime.of(2026, 3, 8, 12, 0)));

		Optional<NotificationGroupDetailResult> result = queryService.getGroupDetail(10L);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().notifications()).hasSize(1);
		assertThat(result.orElseThrow().notifications().getFirst().notificationId()).isEqualTo(101L);
		assertThat(result.orElseThrow().notifications().getFirst().isRead()).isTrue();
		assertThat(result.orElseThrow().notifications().getFirst().readAt())
			.isEqualTo(LocalDateTime.of(2026, 3, 8, 12, 0));
		verify(groupRepository).findByIdWithNotifications(10L);
	}

	@Test
	@DisplayName("그룹 상세 조회 시 7일 이전 데이터는 반환하지 않는다")
	void getGroupDetail_returnsEmptyWhenOlderThanSevenDays() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		when(group.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(8));
		when(groupRepository.findByIdWithNotifications(10L)).thenReturn(Optional.of(group));

		Optional<NotificationGroupDetailResult> result = queryService.getGroupDetail(10L);

		assertThat(result).isEmpty();
		verify(groupRepository).findByIdWithNotifications(10L);
	}

	@Test
	@DisplayName("알림 단건 조회 시 결과를 매핑해 반환한다")
	void getNotification_returnsMappedNotification() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		Notification notification = org.mockito.Mockito.mock(Notification.class);
		when(group.getId()).thenReturn(20L);
		when(group.getSender()).thenReturn("sender");
		when(group.getTitle()).thenReturn("title");
		when(group.getChannelType()).thenReturn(ChannelType.SMS);
		when(notification.getId()).thenReturn(1L);
		when(notification.getReceiver()).thenReturn("01012345678");
		when(notification.getStatus()).thenReturn(NotificationStatus.SENT);
		when(notification.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(1));
		when(notification.getGroup()).thenReturn(group);
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
		when(notificationReadStatusRepository.findReadAtByNotificationId(1L))
			.thenReturn(LocalDateTime.of(2026, 3, 8, 12, 0));

		Optional<NotificationResult> result = queryService.getNotification(1L);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().groupId()).isEqualTo(20L);
		assertThat(result.orElseThrow().receiver()).isEqualTo("01012345678");
		assertThat(result.orElseThrow().isRead()).isTrue();
		assertThat(result.orElseThrow().readAt()).isEqualTo(LocalDateTime.of(2026, 3, 8, 12, 0));
		verify(notificationRepository).findById(1L);
	}

	@Test
	@DisplayName("알림 단건 조회 시 7일 이전 데이터는 반환하지 않는다")
	void getNotification_returnsEmptyWhenOlderThanSevenDays() {
		Notification notification = org.mockito.Mockito.mock(Notification.class);
		when(notification.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(8));
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

		Optional<NotificationResult> result = queryService.getNotification(1L);

		assertThat(result).isEmpty();
		verify(notificationRepository).findById(1L);
	}

	@Test
	@DisplayName("읽지 않은 알림 개수는 최근 7일 + clientId + receiver 기준으로 DB를 직접 조회한다")
	void getUnreadCount_returnsCount() {
		when(notificationRepository.countUnreadByClientIdAndReceiver(
			org.mockito.ArgumentMatchers.eq("client-1"),
			org.mockito.ArgumentMatchers.eq("user@example.com"),
			any(LocalDateTime.class)
		)).thenReturn(7L);

		NotificationUnreadCountResult result = queryService.getUnreadCount("client-1", "user@example.com");

		assertThat(result.receiver()).isEqualTo("user@example.com");
		assertThat(result.unreadCount()).isEqualTo(7L);
		verify(notificationRepository).countUnreadByClientIdAndReceiver(
			org.mockito.ArgumentMatchers.eq("client-1"),
			org.mockito.ArgumentMatchers.eq("user@example.com"),
			any(LocalDateTime.class)
		);
	}

}
