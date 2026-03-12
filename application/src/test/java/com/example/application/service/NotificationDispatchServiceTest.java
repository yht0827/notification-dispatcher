package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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
import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.result.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

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

	@Mock
	private NotificationUnreadCountCacheRepository unreadCountCacheRepository;

	@InjectMocks
	private NotificationDispatchService dispatchService;

	@BeforeEach
	void setUp() {
		lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> invocation.getArgument(0,
			org.springframework.transaction.support.TransactionCallback.class).doInTransaction(null));
		lenient().when(notificationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(unreadCountCacheRepository.enabled()).thenReturn(true);
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
		verify(notificationGroupRepository).bulkApplyDispatchCounts(anyList());
		verify(unreadCountCacheRepository, never()).evict(any(), any());
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

	@Test
	@DisplayName("markAsFailed는 알림 상태를 FAILED로 전환하고 unread count를 decrement한다")
	void markAsFailed_decrementsUnreadCount() {
		Notification notification = createNotification(5L, "user@example.com");
		notification.startSending();
		when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));
		when(notificationRepository.save(notification)).thenReturn(notification);

		dispatchService.markAsFailed(5L, "최종 실패 사유");

		verify(notificationRepository).save(notification);
		verify(unreadCountCacheRepository).decrement("dispatch-service", "user@example.com");
	}

	@Test
	@DisplayName("markAsFailed에서 캐시가 비활성화되어 있으면 decrement를 호출하지 않는다")
	void markAsFailed_skipsDecrementWhenCacheDisabled() {
		when(unreadCountCacheRepository.enabled()).thenReturn(false);
		Notification notification = createNotification(6L, "user@example.com");
		notification.startSending();
		when(notificationRepository.findById(6L)).thenReturn(Optional.of(notification));
		when(notificationRepository.save(notification)).thenReturn(notification);

		dispatchService.markAsFailed(6L, "실패");

		verify(unreadCountCacheRepository, never()).decrement(any(), any());
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
