package com.example.infrastructure.polling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStatus;

@ExtendWith(MockitoExtension.class)
class NotificationRecoveryPollerTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationEventPublisher eventPublisher;

	private NotificationRecoveryPoller recoveryPoller;

	@BeforeEach
	void setUp() {
		recoveryPoller = new NotificationRecoveryPoller(
			notificationRepository,
			eventPublisher,
			new RecoveryProperties(5, 10)
		);
	}

	@Test
	@DisplayName("복구 대상이 없으면 아무 작업도 하지 않는다")
	void recoverStuckNotifications_doesNothingWhenNoNotifications() {
		when(notificationRepository.findByStatusAndCreatedAtBefore(eq(NotificationStatus.PENDING), any(LocalDateTime.class), eq(5)))
			.thenReturn(List.of());

		recoveryPoller.recoverStuckNotifications();

		verify(eventPublisher, never()).publish(any());
	}

	@Test
	@DisplayName("복구 대상이 있으면 모두 다시 발행한다")
	void recoverStuckNotifications_republishesAllRecoverableNotifications() {
		Notification first = notification(101L);
		Notification second = notification(102L);
		when(notificationRepository.findByStatusAndCreatedAtBefore(eq(NotificationStatus.PENDING), any(LocalDateTime.class), eq(5)))
			.thenReturn(List.of(first, second));

		recoveryPoller.recoverStuckNotifications();

		verify(eventPublisher).publish(101L);
		verify(eventPublisher).publish(102L);
	}

	@Test
	@DisplayName("일부 재발행 실패가 있어도 나머지는 계속 진행한다")
	void recoverStuckNotifications_continuesWhenOnePublishFails() {
		Notification first = notification(201L);
		Notification second = notification(202L);
		when(notificationRepository.findByStatusAndCreatedAtBefore(eq(NotificationStatus.PENDING), any(LocalDateTime.class), eq(5)))
			.thenReturn(List.of(first, second));
		doThrow(new IllegalStateException("publish failed")).when(eventPublisher).publish(201L);

		recoveryPoller.recoverStuckNotifications();

		verify(eventPublisher).publish(201L);
		verify(eventPublisher).publish(202L);
	}

	private Notification notification(Long id) {
		NotificationGroup group = NotificationGroup.create(
			"recovery-service",
			"idem-" + id,
			"MyShop",
			"title",
			"content",
			ChannelType.EMAIL,
			1
		);
		Notification notification = group.addNotification("user@example.com");
		try {
			var field = Notification.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(notification, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return notification;
	}
}
