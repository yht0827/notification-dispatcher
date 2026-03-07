package com.example.application.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationListResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStatus;

class NotificationResultMapperTest {

	private final NotificationResultMapper mapper = new NotificationResultMapper();

	@Test
	@DisplayName("NotificationGroup을 NotificationGroupResult로 변환한다")
	void toGroupResult() {
		// given
		NotificationGroup group = mock(NotificationGroup.class);
		when(group.getId()).thenReturn(1L);
		when(group.getClientId()).thenReturn("client-1");
		when(group.getSender()).thenReturn("sender");
		when(group.getTitle()).thenReturn("title");
		when(group.getGroupType()).thenReturn(GroupType.SINGLE);
		when(group.getChannelType()).thenReturn(ChannelType.SMS);
		when(group.getTotalCount()).thenReturn(10);
		when(group.getSentCount()).thenReturn(5);
		when(group.getFailedCount()).thenReturn(2);
		when(group.getPendingCount()).thenReturn(3);
		when(group.isCompleted()).thenReturn(false);
		when(group.getCreatedAt()).thenReturn(LocalDateTime.now());

		// when
		NotificationGroupResult result = mapper.toGroupResult(group);

		// then
		assertThat(result.id()).isEqualTo(1L);
		assertThat(result.clientId()).isEqualTo("client-1");
		assertThat(result.totalCount()).isEqualTo(10);
	}

	@Test
	@DisplayName("NotificationGroup을 NotificationListResult로 변환한다 - moreCount 계산 확인")
	void toListResult() {
		// given
		NotificationGroup group = mock(NotificationGroup.class);
		when(group.getId()).thenReturn(1L);
		when(group.getTitle()).thenReturn("title");
		when(group.getContent()).thenReturn("content");
		when(group.getTotalCount()).thenReturn(5);
		when(group.getCreatedAt()).thenReturn(LocalDateTime.now());

		// when
		NotificationListResult result = mapper.toListResult(group);

		// then
		assertThat(result.groupId()).isEqualTo(1L);
		assertThat(result.totalCount()).isEqualTo(5);
		assertThat(result.moreCount()).isEqualTo(4); // totalCount - 1
	}

	@Test
	@DisplayName("Notification을 NotificationResult로 변환한다")
	void toNotificationResult() {
		// given
		NotificationGroup group = mock(NotificationGroup.class);
		when(group.getId()).thenReturn(100L);
		when(group.getSender()).thenReturn("sender");
		when(group.getChannelType()).thenReturn(ChannelType.EMAIL);

		Notification notification = mock(Notification.class);
		when(notification.getId()).thenReturn(1L);
		when(notification.getReceiver()).thenReturn("receiver");
		when(notification.getStatus()).thenReturn(NotificationStatus.SENT);
		when(notification.getGroup()).thenReturn(group);

		// when
		NotificationResult result = mapper.toNotificationResult(notification);

		// then
		assertThat(result.id()).isEqualTo(1L);
		assertThat(result.groupId()).isEqualTo(100L);
		assertThat(result.receiver()).isEqualTo("receiver");
		assertThat(result.channelType()).isEqualTo(ChannelType.EMAIL);
		assertThat(result.isRead()).isFalse();
	}

	@Test
	@DisplayName("NotificationGroup을 NotificationGroupDetailResult로 변환한다")
	void toGroupDetailResult() {
		NotificationGroup group = mock(NotificationGroup.class);
		Notification notification = mock(Notification.class);
		when(group.getId()).thenReturn(1L);
		when(group.getClientId()).thenReturn("client-1");
		when(group.getSender()).thenReturn("sender");
		when(group.getTitle()).thenReturn("title");
		when(group.getContent()).thenReturn("content");
		when(group.getGroupType()).thenReturn(GroupType.BULK);
		when(group.getChannelType()).thenReturn(ChannelType.EMAIL);
		when(group.getTotalCount()).thenReturn(1);
		when(group.getSentCount()).thenReturn(0);
		when(group.getFailedCount()).thenReturn(0);
		when(group.getPendingCount()).thenReturn(1);
		when(group.isCompleted()).thenReturn(false);
		when(group.getNotifications()).thenReturn(Collections.singletonList(notification));
		when(notification.getId()).thenReturn(101L);
		when(notification.getReceiver()).thenReturn("user@example.com");
		when(notification.getStatus()).thenReturn(NotificationStatus.PENDING);

		NotificationGroupDetailResult result = mapper.toGroupDetailResult(group);

		assertThat(result.groupId()).isEqualTo(1L);
		assertThat(result.notifications()).hasSize(1);
		assertThat(result.notifications().getFirst().notificationId()).isEqualTo(101L);
		assertThat(result.notifications().getFirst().receiver()).isEqualTo("user@example.com");
	}
}
