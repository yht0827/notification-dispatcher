package com.example.worker.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.DispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.worker.messaging.inbound.RabbitMQRecordHandler;
import com.example.worker.messaging.inbound.RecordProcessRequest;
import com.example.worker.messaging.inbound.RecordProcessResult;

@ExtendWith(MockitoExtension.class)
class RabbitMQRecordHandlerTest {

	@Mock
	private NotificationDispatchUseCase dispatchService;

	@Mock
	private DispatchLockManager lockManager;

	private RabbitMQRecordHandler recordHandler;

	@BeforeEach
	void setUp() {
		lenient().when(lockManager.tryAcquire(any())).thenReturn(true);
		recordHandler = new RabbitMQRecordHandler(dispatchService, lockManager);
	}

	@Test
	@DisplayName("notificationId가 null이면 non-retryable 실패를 반환한다")
	void process_returnsNonRetryableFailureWhenNotificationIdIsNull() {
		RecordProcessResult result = recordHandler.process(new RecordProcessRequest(1L, null, 0));

		assertThat(result.isNonRetryableFailure()).isTrue();
		verify(lockManager, never()).tryAcquire(any());
	}

	@Test
	@DisplayName("락 획득 실패 시 skipped 결과를 반환한다")
	void process_returnsSkippedWhenLockNotAcquired() {
		when(lockManager.tryAcquire(101L)).thenReturn(false);

		RecordProcessResult result = recordHandler.process(new RecordProcessRequest(1L, 101L, 0));

		assertThat(result.isSkipped()).isTrue();
		verify(dispatchService, never()).dispatch(any(Long.class));
	}

	@Test
	@DisplayName("알림을 찾을 수 없으면 non-retryable 실패를 반환한다")
	void process_returnsNonRetryableFailureWhenNotificationNotFound() {
		when(dispatchService.dispatch(201L)).thenReturn(DispatchResult.failNonRetryable(201L, "알림을 찾을 수 없음: 201"));

		RecordProcessResult result = recordHandler.process(new RecordProcessRequest(2L, 201L, 0));

		assertThat(result.isNonRetryableFailure()).isTrue();
	}

	@Test
	@DisplayName("발송 성공 시 success를 반환하고 락을 해제한다")
	void process_returnsSuccessAndReleasesLock() {
		when(dispatchService.dispatch(301L)).thenReturn(DispatchResult.success(301L));

		RecordProcessResult result = recordHandler.process(new RecordProcessRequest(3L, 301L, 0));

		assertThat(result.isSuccess()).isTrue();
		verify(lockManager).release(301L);
	}

	@Test
	@DisplayName("retryable 실패 시 retryable 결과를 반환하고 락을 해제한다")
	void process_returnsRetryableFailureAndReleasesLock() {
		when(dispatchService.dispatch(401L)).thenReturn(DispatchResult.failRetryable(401L, "일시 오류"));

		RecordProcessResult result = recordHandler.process(new RecordProcessRequest(4L, 401L, 1));

		assertThat(result.isRetryableFailure()).isTrue();
		assertThat(result.reason()).isEqualTo("일시 오류");
		verify(lockManager).release(401L);
	}

	@Test
	@DisplayName("non-retryable 실패 시 markAsFailed를 호출하고 락을 유지한다")
	void process_callsMarkAsFailedAndKeepsLockOnNonRetryableFailure() {
		when(dispatchService.dispatch(501L)).thenReturn(DispatchResult.failNonRetryable(501L, "주소 오류"));

		RecordProcessResult result = recordHandler.process(new RecordProcessRequest(5L, 501L, 0));

		assertThat(result.isNonRetryableFailure()).isTrue();
		verify(dispatchService).markAsFailed(501L, "주소 오류");
		verify(lockManager, never()).release(501L);
	}

	@Test
	@DisplayName("낙관적 락 충돌 시 skipped를 반환하고 락을 해제한다")
	void process_returnsSkippedAndReleasesLockOnOptimisticLockingFailure() {
		when(dispatchService.dispatch(601L))
			.thenThrow(new OptimisticLockingFailureException("version mismatch"));

		RecordProcessResult result = recordHandler.process(new RecordProcessRequest(6L, 601L, 0));

		assertThat(result.isSkipped()).isTrue();
		verify(lockManager).release(601L);
	}
}
