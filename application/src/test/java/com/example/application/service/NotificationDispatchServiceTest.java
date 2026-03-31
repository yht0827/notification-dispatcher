package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.application.port.in.result.DispatchResult;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.SendResult;
import com.example.application.port.out.event.AdminStatsChangedEvent;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationSender notificationSender;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private NotificationDispatchService dispatchService;

	@BeforeEach
	void setUp() {
		dispatchService = new NotificationDispatchService(
			notificationRepository,
			notificationSender,
			eventPublisher
		);
	}

	@Test
	@DisplayName("발송 성공 시 결과를 반환하고 상태를 반영한다")
	void dispatch_returnsSuccessResults() {
		Notification first = createNotification(1L, "first@example.com");
		Notification second = createNotification(2L, "second@example.com");
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(first));
		when(notificationRepository.findById(2L)).thenReturn(Optional.of(second));
		when(notificationSender.send(first)).thenReturn(SendResult.success());
		when(notificationSender.send(second)).thenReturn(SendResult.success());

		DispatchResult result1 = dispatchService.dispatch(1L);
		DispatchResult result2 = dispatchService.dispatch(2L);

		assertThat(result1.notificationId()).isEqualTo(1L);
		assertThat(result1.isSuccess()).isTrue();
		assertThat(result2.notificationId()).isEqualTo(2L);
		assertThat(result2.isSuccess()).isTrue();
		verify(notificationSender, times(2)).send(any(Notification.class));
		verify(eventPublisher, times(2)).publishEvent(any(AdminStatsChangedEvent.class));
	}

	@Test
	@DisplayName("terminal 알림은 발송을 건너뛰고 나머지만 전송한다")
	void dispatch_skipsTerminalNotifications() {
		Notification terminal = createNotification(1L, "done@example.com");
		terminal.startSending();
		terminal.markAsSent();
		Notification pending = createNotification(2L, "pending@example.com");
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(terminal));
		when(notificationRepository.findById(2L)).thenReturn(Optional.of(pending));
		when(notificationSender.send(pending)).thenReturn(SendResult.success());

		DispatchResult terminalResult = dispatchService.dispatch(1L);
		DispatchResult pendingResult = dispatchService.dispatch(2L);

		assertThat(terminalResult.isSuccess()).isTrue();
		assertThat(pendingResult.isSuccess()).isTrue();
		verify(notificationSender, times(1)).send(any(Notification.class));
	}

	@Test
	@DisplayName("non-retryable 실패는 FAILED 반영 후 결과로 반환한다")
	void dispatch_marksFailedForNonRetryableFailure() {
		Notification pending = createNotification(10L, "failed@example.com");
		when(notificationRepository.findById(10L)).thenReturn(Optional.of(pending));
		when(notificationSender.send(pending)).thenReturn(SendResult.failNonRetryable("주소 오류"));

		DispatchResult result = dispatchService.dispatch(10L);

		assertThat(result.isNonRetryableFailure()).isTrue();
		assertThat(result.failReason()).isEqualTo("주소 오류");
		verify(eventPublisher, times(1)).publishEvent(any(AdminStatsChangedEvent.class));
	}

	@Test
	@DisplayName("markAsFailed는 알림 상태를 FAILED로 전환한다")
	void markAsFailed_marksNotificationAsFailed() {
		Notification notification = createNotification(5L, "user@example.com");
		notification.startSending();
		when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));
		when(notificationRepository.save(notification)).thenReturn(notification);

		dispatchService.markAsFailed(5L, "최종 실패 사유");

		verify(notificationRepository).save(notification);
		verify(eventPublisher).publishEvent(any(AdminStatsChangedEvent.class));
	}

	private Notification createNotification(Long id, String receiver) {
		NotificationGroup group = NotificationGroup.create(
			"dispatch-service",
			"group-idem",
			"MyShop",
			"테스트",
			"테스트 내용",
			ChannelType.EMAIL,
			1
		);
		ReflectionTestUtils.setField(group, "id", id != null ? id + 1_000L : 1_000L);
		Notification notification = group.addNotification(receiver);
		if (id != null) {
			ReflectionTestUtils.setField(notification, "id", id);
		}
		return notification;
	}
}
