package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.in.result.BatchDispatchResult;
import com.example.application.port.in.result.NotificationDispatchResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
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
	private NotificationGroupRepository notificationGroupRepository;

	@Mock
	private NotificationSender notificationSender;

	@Mock
	private TransactionTemplate transactionTemplate;

	@InjectMocks
	private NotificationDispatchService dispatchService;

	@BeforeEach
	void setUp() {
		lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> invocation.getArgument(0,
			org.springframework.transaction.support.TransactionCallback.class).doInTransaction(null));
		lenient().when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
	}

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

	@Test
	@DisplayName("배치 발송 성공 시 결과를 모아 반환하고 상태를 일괄 반영한다")
	void dispatchBatch_returnsSuccessResults() {
		Notification first = createNotification(1L, "first@example.com");
		Notification second = createNotification(2L, "second@example.com");
		when(notificationSender.send(first)).thenReturn(SendResult.success());
		when(notificationSender.send(second)).thenReturn(SendResult.success());

		List<BatchDispatchResult> results = dispatchService.dispatchBatch(List.of(first, second));

		assertThat(results).extracting(BatchDispatchResult::notificationId).containsExactly(1L, 2L);
		assertThat(results).allMatch(BatchDispatchResult::isSuccess);
		verify(notificationRepository).bulkStartSending(eq(List.of(1L, 2L)), any());
		verify(notificationRepository).bulkMarkAsSent(eq(List.of(1L, 2L)), any(), any());
		verify(notificationSender, times(2)).send(any(Notification.class));
		verifyNoInteractions(notificationGroupRepository);
	}

	@Test
	@DisplayName("배치 발송은 terminal 알림을 건너뛰고 나머지만 전송한다")
	void dispatchBatch_skipsTerminalNotifications() {
		Notification terminal = createNotification(1L, "done@example.com");
		terminal.startSending();
		terminal.markAsSent();
		Notification pending = createNotification(2L, "pending@example.com");
		when(notificationSender.send(pending)).thenReturn(SendResult.success());

		List<BatchDispatchResult> results = dispatchService.dispatchBatch(List.of(terminal, pending));

		assertThat(results).hasSize(2);
		assertThat(results.get(0).isSuccess()).isTrue();
		assertThat(results.get(1).isSuccess()).isTrue();
		verify(notificationSender, times(1)).send(any(Notification.class));
	}

	@Test
	@DisplayName("배치 발송에서 non-retryable 실패는 FAILED 반영 후 결과로 반환한다")
	void dispatchBatch_marksFailedForNonRetryableFailure() {
		Notification pending = createNotification(10L, "failed@example.com");

		when(notificationSender.send(pending)).thenReturn(SendResult.failNonRetryable("주소 오류"));

		List<BatchDispatchResult> results = dispatchService.dispatchBatch(List.of(pending));

		assertThat(results).singleElement().satisfies(result -> {
			assertThat(result.isNonRetryableFailure()).isTrue();
			assertThat(result.failReason()).isEqualTo("주소 오류");
		});
		verify(notificationRepository).bulkStartSending(eq(List.of(10L)), any());
		verify(notificationRepository).bulkMarkAsFailed(anyList(), any());
	}

	private Notification createNotification() {
		return createNotification(null, "user@example.com");
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
		Notification notification = group.addNotification(receiver);
		if (id != null) {
			ReflectionTestUtils.setField(notification, "id", id);
		}
		return notification;
	}

	private Notification createSendingNotification(Long id, String receiver) {
		Notification notification = createNotification(id, receiver);
		notification.startSending();
		return notification;
	}
}
