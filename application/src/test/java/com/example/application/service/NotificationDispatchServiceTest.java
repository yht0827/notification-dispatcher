package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.in.result.NotificationDispatchResult;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.result.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStatus;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationSender notificationSender;

	@InjectMocks
	private NotificationDispatchService dispatchService;

	@Test
	@DisplayName("이미 종결 상태인 알림은 발송을 생략한다")
	void dispatch_skipsWhenNotificationAlreadyTerminal() {
		// given
		Notification notification = createNotification();
		notification.startSending();
		notification.markAsSent();

		// when
		dispatchService.dispatch(notification);

		// then
		verifyNoInteractions(notificationSender);
		verify(notificationRepository, never()).save(any(Notification.class));
	}

	@Test
	@DisplayName("정상 발송하고 상태를 저장한다")
	void dispatch_sendsAndPersists() {
		// given
		Notification notification = createNotification();
		when(notificationRepository.save(notification)).thenReturn(notification);
		when(notificationSender.send(notification)).thenReturn(SendResult.success());

		// when
		NotificationDispatchResult result = dispatchService.dispatch(notification);

		// then
		assertThat(result.isSuccess()).isTrue();
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		verify(notificationSender).send(notification);
		verify(notificationRepository, times(2)).save(notification);
	}

	@Test
	@DisplayName("save가 반환한 관리 엔티티를 사용해 발송한다")
	void dispatch_usesManagedEntityReturnedFromSave() {
		// given
		Notification detached = createNotification();
		Notification managed = createNotification();
		managed.startSending();

		when(notificationRepository.save(detached)).thenReturn(managed);
		when(notificationRepository.save(managed)).thenReturn(managed);
		when(notificationSender.send(managed)).thenReturn(SendResult.success());

		// when
		NotificationDispatchResult result = dispatchService.dispatch(detached);

		// then
		assertThat(result.isSuccess()).isTrue();
		verify(notificationRepository).save(detached);
		verify(notificationSender).send(managed);
		verify(notificationRepository).save(managed);
	}

	@Test
	@DisplayName("SENDING 상태 재시도에서도 발송을 계속 진행한다")
	void dispatch_continuesWhenAlreadySendingForRetry() {
		// given
		Notification notification = createNotification();
		notification.startSending();
		when(notificationRepository.save(notification)).thenReturn(notification);
		when(notificationSender.send(notification)).thenReturn(SendResult.success());

		// when
		NotificationDispatchResult result = dispatchService.dispatch(notification);

		// then
		assertThat(result.isSuccess()).isTrue();
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getAttemptCount()).isEqualTo(2);
		verify(notificationSender).send(notification);
		verify(notificationRepository, times(2)).save(notification);
	}

	@Test
	@DisplayName("발송 실패 시 실패 결과를 반환한다")
	void dispatch_returnsFailureWhenSendFails() {
		// given
		Notification notification = createNotification();
		when(notificationRepository.save(notification)).thenReturn(notification);
		when(notificationSender.send(notification)).thenReturn(SendResult.fail("발송 실패"));

		// when
		NotificationDispatchResult result = dispatchService.dispatch(notification);

		// then
		assertThat(result.isFailure()).isTrue();
		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.failReason()).isEqualTo("발송 실패");
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENDING);
		verify(notificationSender).send(notification);
		verify(notificationRepository).save(notification);
	}

	@Test
	@DisplayName("재시도 불가 실패는 non-retryable 결과를 반환한다")
	void dispatch_returnsNonRetryableFailureWhenSendFailsNonRetryable() {
		// given
		Notification notification = createNotification();
		when(notificationRepository.save(notification)).thenReturn(notification);
		when(notificationSender.send(notification)).thenReturn(SendResult.failNonRetryable("수신자 주소 오류"));

		// when
		NotificationDispatchResult result = dispatchService.dispatch(notification);

		// then
		assertThat(result.isFailure()).isTrue();
		assertThat(result.isNonRetryableFailure()).isTrue();
		assertThat(result.failReason()).isEqualTo("수신자 주소 오류");
		verify(notificationSender).send(notification);
		verify(notificationRepository).save(notification);
	}

	private Notification createNotification() {
		NotificationGroup group = NotificationGroup.create(
			"dispatch-service",
			"group-idem",
			"MyShop",
			"테스트",
			"테스트 내용",
			ChannelType.EMAIL,
			1
		);
		return group.addNotification("user@example.com");
	}
}
