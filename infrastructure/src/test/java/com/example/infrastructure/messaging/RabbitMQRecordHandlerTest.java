package com.example.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.BatchDispatchResult;
import com.example.application.port.in.result.NotificationDispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.exception.InvalidStatusTransitionException;
import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.exception.NonRetryableMessageException;
import com.example.infrastructure.messaging.exception.RetryableMessageException;
import com.example.infrastructure.messaging.inbound.RabbitMQRecordHandler;
import com.example.infrastructure.messaging.inbound.RecordProcessRequest;
import com.example.infrastructure.messaging.inbound.RecordProcessResult;

@ExtendWith(MockitoExtension.class)
class RabbitMQRecordHandlerTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationDispatchUseCase dispatchService;

	@Mock
	private NotificationRabbitProperties properties;

	@Mock
	private DispatchLockManager lockManager;

	private RabbitMQRecordHandler recordHandler;

	@BeforeEach
	void setUp() {
		lenient().when(lockManager.tryAcquire(any())).thenReturn(true);
		recordHandler = new RabbitMQRecordHandler(notificationRepository, dispatchService, properties, lockManager);
	}

	@Test
	@DisplayName("종결 상태 알림도 dispatch 서비스에 위임한다")
	void process_delegatesTerminalNotificationToDispatchService() {
		// given
		Notification notification = createNotification();
		notification.startSending();
		notification.markAsSent();
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenReturn(NotificationDispatchResult.success());

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
		when(dispatchService.dispatch(notification)).thenReturn(NotificationDispatchResult.fail("발송 실패"));

		// when & then
		assertThatThrownBy(() -> recordHandler.process(1L, 0))
			.isInstanceOf(RetryableMessageException.class)
			.hasMessageContaining("발송 실패");
		verify(lockManager).release(1L);
	}

	@Test
	@DisplayName("429 실패에 전달된 retry delay는 WAIT 경로까지 유지한다")
	void process_preservesRetryDelayForRateLimitFailure() {
		Notification notification = createNotification();
		when(notificationRepository.findById(12L)).thenReturn(Optional.of(notification));
		when(properties.resolveMaxRetryCount()).thenReturn(3);
		when(dispatchService.dispatch(notification))
			.thenReturn(NotificationDispatchResult.failRetryable("rate limit", 15_000L));

		assertThatThrownBy(() -> recordHandler.process(12L, 0))
			.isInstanceOfSatisfying(RetryableMessageException.class,
				exception -> assertThat(exception.retryDelayMillis()).isEqualTo(15_000L));

		verify(lockManager).release(12L);
	}

	@Test
	@DisplayName("재시도 불가 발송 실패는 즉시 NonRetryable로 처리한다")
	void process_throwsNonRetryableWhenDispatchFailureIsNonRetryable() {
		// given
		Notification notification = createNotification();
		when(notificationRepository.findById(11L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenReturn(NotificationDispatchResult.failNonRetryable("수신자 주소 오류"));

		// when & then
		assertThatThrownBy(() -> recordHandler.process(11L, 0))
			.isInstanceOf(NonRetryableMessageException.class)
			.hasMessageContaining("재시도 불가 발송 실패");

		verify(dispatchService).markAsFailed(11L, "수신자 주소 오류");
		verify(lockManager, never()).release(11L);
	}

	@Test
	@DisplayName("재시도 한도 초과 시 NonRetryable 예외를 던진다")
	void process_throwsNonRetryableWhenMaxRetryExceeded() {
		// given
		Notification notification = createNotification();
		when(notificationRepository.findById(2L)).thenReturn(Optional.of(notification));
		when(properties.resolveMaxRetryCount()).thenReturn(3);
		when(dispatchService.dispatch(notification)).thenReturn(NotificationDispatchResult.fail("발송 실패"));

		// when & then
		assertThatThrownBy(() -> recordHandler.process(2L, 3))
			.isInstanceOf(NonRetryableMessageException.class)
			.hasMessageContaining("재시도 한도 초과");
		verify(dispatchService).markAsFailed(2L, "발송 실패");
	}

	@Test
	@DisplayName("notificationId가 null이면 non-retryable 예외를 던진다")
	void process_throwsNonRetryableWhenNotificationIdIsNull() {
		assertThatThrownBy(() -> recordHandler.process(null, 0))
			.isInstanceOf(NonRetryableMessageException.class)
			.hasMessageContaining("notificationId");
	}

	@Test
	@DisplayName("알림을 찾을 수 없으면 non-retryable 예외를 던진다")
	void process_throwsNonRetryableWhenNotificationNotFound() {
		when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> recordHandler.process(999L, 0))
			.isInstanceOf(NonRetryableMessageException.class)
			.hasMessageContaining("알림을 찾을 수 없음");
		verify(lockManager, never()).release(999L);
	}

	@Test
	@DisplayName("락 획득에 실패하면 처리 로직을 스킵한다")
	void process_skipsWhenLockNotAcquired() {
		when(lockManager.tryAcquire(10L)).thenReturn(false);

		recordHandler.process(10L, 0);

		verifyNoInteractions(notificationRepository, dispatchService, properties);
		verify(lockManager, never()).release(10L);
	}

	@Test
	@DisplayName("예상치 못한 예외는 retryable로 감싸고 락을 해제한다")
	void process_wrapsUnexpectedExceptionAndReleasesLock() {
		Notification notification = createNotification();
		when(notificationRepository.findById(20L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenThrow(new IllegalStateException("boom"));

		assertThatThrownBy(() -> recordHandler.process(20L, 0))
			.isInstanceOf(RetryableMessageException.class)
			.hasMessageContaining("예상치 못한 오류");

		verify(lockManager).release(20L);
	}

	@Test
	@DisplayName("지원하지 않는 채널 예외는 non-retryable로 전환한다")
	void process_convertsUnsupportedChannelToNonRetryable() {
		Notification notification = createNotification();
		when(notificationRepository.findById(3L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenThrow(new UnsupportedChannelException(ChannelType.EMAIL));

		assertThatThrownBy(() -> recordHandler.process(3L, 0))
			.isInstanceOf(NonRetryableMessageException.class)
			.hasMessageContaining("지원하지 않는 채널");

		verify(dispatchService).markAsFailed(3L, "지원하지 않는 채널입니다: EMAIL");
	}

	@Test
	@DisplayName("상태 전이 예외는 non-retryable로 전환한다")
	void process_convertsInvalidStatusTransitionToNonRetryable() {
		Notification notification = createNotification();
		when(notificationRepository.findById(4L)).thenReturn(Optional.of(notification));
		when(dispatchService.dispatch(notification)).thenThrow(
			new InvalidStatusTransitionException("invalid transition"));

		assertThatThrownBy(() -> recordHandler.process(4L, 0))
			.isInstanceOf(NonRetryableMessageException.class)
			.hasMessageContaining("상태 전이 오류");

		verify(dispatchService).markAsFailed(4L, "상태 전이 오류 - invalid transition");
	}

	@Test
	@DisplayName("배치 처리 성공 시 성공 결과를 반환하고 락을 해제한다")
	void processBatch_returnsSuccessAndReleasesLock() {
		Notification first = createNotification(101L, "first@example.com");
		Notification second = createNotification(102L, "second@example.com");
		when(notificationRepository.findAllByIdIn(List.of(101L, 102L))).thenReturn(List.of(first, second));
		when(dispatchService.dispatchBatch(List.of(first, second))).thenReturn(List.of(
			BatchDispatchResult.success(101L),
			BatchDispatchResult.success(102L)
		));

		List<RecordProcessResult> results = recordHandler.processBatch(List.of(
			new RecordProcessRequest(1L, 101L, 0),
			new RecordProcessRequest(2L, 102L, 1)
		));

		assertThat(results).extracting(RecordProcessResult::status)
			.containsExactly(RecordProcessResult.Status.SUCCESS, RecordProcessResult.Status.SUCCESS);
		verify(lockManager).release(101L);
		verify(lockManager).release(102L);
	}

	@Test
	@DisplayName("동일 배치 내 중복 notificationId는 첫 건만 처리하고 나머지는 스킵한다")
	void processBatch_skipsDuplicateIdsWithinSingleBatch() {
		Notification first = createNotification(201L, "duplicate@example.com");
		when(notificationRepository.findAllByIdIn(List.of(201L))).thenReturn(List.of(first));
		when(dispatchService.dispatchBatch(List.of(first))).thenReturn(List.of(BatchDispatchResult.success(201L)));

		List<RecordProcessResult> results = recordHandler.processBatch(List.of(
			new RecordProcessRequest(1L, 201L, 0),
			new RecordProcessRequest(2L, 201L, 1)
		));

		assertThat(results.get(0).isSuccess()).isTrue();
		assertThat(results.get(1).isSkipped()).isTrue();
		verify(dispatchService).dispatchBatch(List.of(first));
	}

	@Test
	@DisplayName("배치 처리 재시도 실패는 retryable 결과로 반환하고 락을 해제한다")
	void processBatch_returnsRetryableFailureAndReleasesLock() {
		Notification notification = createNotification(301L, "retryable@example.com");
		when(notificationRepository.findAllByIdIn(List.of(301L))).thenReturn(List.of(notification));
		when(dispatchService.dispatchBatch(List.of(notification))).thenReturn(List.of(
			BatchDispatchResult.failRetryable(301L, "일시 오류")
		));

		List<RecordProcessResult> results = recordHandler.processBatch(List.of(
			new RecordProcessRequest(1L, 301L, 2)
		));

		assertThat(results).singleElement().satisfies(result -> {
			assertThat(result.isRetryableFailure()).isTrue();
			assertThat(result.reason()).isEqualTo("일시 오류");
		});
		verify(lockManager).release(301L);
	}

	private Notification createNotification() {
		return createNotification(null, "user@example.com");
	}

	private Notification createNotification(Long id, String receiver) {
		NotificationGroup group = NotificationGroup.create(
			"record-handler",
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
}
