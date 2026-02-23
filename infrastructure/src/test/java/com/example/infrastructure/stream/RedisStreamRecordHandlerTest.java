package com.example.infrastructure.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.NotificationDispatchUseCase.DispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationRepository;
import com.example.domain.exception.InvalidStatusTransitionException;
import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.stream.config.NotificationStreamProperties;
import com.example.infrastructure.stream.inbound.RedisStreamRecordHandler;
import com.example.infrastructure.stream.exception.NonRetryableStreamMessageException;
import com.example.infrastructure.stream.exception.RetryableStreamMessageException;

@ExtendWith(MockitoExtension.class)
class RedisStreamRecordHandlerTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationDispatchUseCase dispatchService;

	@Mock
	private NotificationStreamProperties properties;

	@Mock
	private DispatchLockManager lockManager;

	private RedisStreamRecordHandler recordHandler;

	@BeforeEach
	void setUp() {
		when(lockManager.tryAcquire(any())).thenReturn(true);
		recordHandler = new RedisStreamRecordHandler(notificationRepository, dispatchService, properties, lockManager);
	}

	@Test
	@DisplayName("종결 상태 알림도 dispatch 서비스에 위임한다")
	void process_delegatesTerminalNotificationToDispatchService() {
		// given
		Notification notification = createNotification();
		notification.startSending();
		notification.markAsSent();
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenReturn(DispatchResult.success());

		// when
		recordHandler.process(1L, 0);

		// then
		verify(dispatchService).dispatch(notification);
	}

	@Test
	@DisplayName("발송 실패 시 재시도 가능하면 Retryable 예외를 던진다")
	void process_throwsRuntimeExceptionWhenSendFailsAndCanRetry() {
		// given
		Notification notification = createNotification();
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
		when(properties.resolveMaxRetryCount()).thenReturn(3);
		when(dispatchService.dispatch(notification)).thenReturn(DispatchResult.fail("발송 실패"));

		// when & then
		assertThatThrownBy(() -> recordHandler.process(1L, 0))
			.isInstanceOf(RetryableStreamMessageException.class)
			.hasMessageContaining("발송 실패");
	}

	@Test
	@DisplayName("재시도 한도 초과 시 NonRetryable 예외를 던진다")
	void process_throwsNonRetryableWhenMaxRetryExceeded() {
		// given
		Notification notification = createNotification();
		when(notificationRepository.findById(2L)).thenReturn(Optional.of(notification));
		when(properties.resolveMaxRetryCount()).thenReturn(3);
		when(dispatchService.dispatch(notification)).thenReturn(DispatchResult.fail("발송 실패"));

		// when & then
		assertThatThrownBy(() -> recordHandler.process(2L, 3))
			.isInstanceOf(NonRetryableStreamMessageException.class)
			.hasMessageContaining("재시도 한도 초과");
		verify(dispatchService).markAsFailed(2L, "발송 실패");
	}

	@Test
	@DisplayName("notificationId가 null이면 non-retryable 예외를 던진다")
	void process_throwsNonRetryableWhenNotificationIdIsNull() {
		assertThatThrownBy(() -> recordHandler.process(null, 0))
			.isInstanceOf(NonRetryableStreamMessageException.class)
			.hasMessageContaining("notificationId");
	}

	@Test
	@DisplayName("알림을 찾을 수 없으면 non-retryable 예외를 던진다")
	void process_throwsNonRetryableWhenNotificationNotFound() {
		when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> recordHandler.process(999L, 0))
			.isInstanceOf(NonRetryableStreamMessageException.class)
			.hasMessageContaining("알림을 찾을 수 없음");
	}

	@Test
	@DisplayName("지원하지 않는 채널 예외는 non-retryable로 전환한다")
	void process_convertsUnsupportedChannelToNonRetryable() {
		Notification notification = createNotification();
		when(notificationRepository.findById(3L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenThrow(new UnsupportedChannelException(ChannelType.EMAIL));

		assertThatThrownBy(() -> recordHandler.process(3L, 0))
			.isInstanceOf(NonRetryableStreamMessageException.class)
			.hasMessageContaining("지원하지 않는 채널");

		verify(dispatchService).markAsFailed(3L, "지원하지 않는 채널입니다: EMAIL");
	}

	@Test
	@DisplayName("상태 전이 예외는 non-retryable로 전환한다")
	void process_convertsInvalidStatusTransitionToNonRetryable() {
		Notification notification = createNotification();
		when(notificationRepository.findById(4L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenThrow(new InvalidStatusTransitionException("invalid transition"));

		assertThatThrownBy(() -> recordHandler.process(4L, 0))
			.isInstanceOf(NonRetryableStreamMessageException.class)
			.hasMessageContaining("상태 전이 오류");

		verify(dispatchService).markAsFailed(4L, "상태 전이 오류");
	}

	private Notification createNotification() {
		NotificationGroup group = NotificationGroup.create(
			"record-handler",
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
