package com.example.worker.messaging.sync;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.event.SyncDispatchEvent;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.service.NotificationDispatchService;
import com.example.domain.notification.Notification;

@ExtendWith(MockitoExtension.class)
class SyncDispatchEventListenerTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationDispatchService notificationDispatchService;

	@Test
	@DisplayName("notificationId가 비어 있으면 동기 발송을 건너뛴다")
	void onSyncDispatch_skipsWhenIdsEmpty() {
		SyncDispatchEventListener listener =
			new SyncDispatchEventListener(notificationRepository, notificationDispatchService);

		listener.onSyncDispatch(new SyncDispatchEvent(List.of()));

		verify(notificationRepository, never()).findAllByIdIn(List.of());
		verify(notificationDispatchService, never()).dispatchBatch(List.of());
	}

	@Test
	@DisplayName("notificationId가 있으면 조회 후 배치 발송을 실행한다")
	void onSyncDispatch_dispatchesLoadedNotifications() {
		SyncDispatchEventListener listener =
			new SyncDispatchEventListener(notificationRepository, notificationDispatchService);
		List<Long> ids = List.of(1L, 2L);
		List<Notification> notifications = List.of(org.mockito.Mockito.mock(Notification.class),
			org.mockito.Mockito.mock(Notification.class));
		when(notificationRepository.findAllByIdIn(ids)).thenReturn(notifications);

		listener.onSyncDispatch(new SyncDispatchEvent(ids));

		verify(notificationRepository).findAllByIdIn(ids);
		verify(notificationDispatchService).dispatchBatch(notifications);
	}
}
