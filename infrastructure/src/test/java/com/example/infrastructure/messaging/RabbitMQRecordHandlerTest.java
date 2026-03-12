package com.example.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.BatchDispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
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
    private DispatchLockManager lockManager;

	private RabbitMQRecordHandler recordHandler;

	@BeforeEach
	void setUp() {
		lenient().when(lockManager.tryAcquire(any())).thenReturn(true);
        recordHandler = new RabbitMQRecordHandler(notificationRepository, dispatchService, lockManager);
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
