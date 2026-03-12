package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.in.result.NotificationGroupReadResult;
import com.example.application.port.in.result.NotificationReadResult;
import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationReadStatusRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationWriteServiceTest {

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationGroupRepository notificationGroupRepository;

	@Mock
	private NotificationReadStatusRepository notificationReadStatusRepository;

	@Mock
	private NotificationIdempotencyLookupService idempotencyLookupService;

	@Mock
	private NotificationWriteExecutor notificationWriteExecutor;

	@Mock
	private NotificationUnreadCountCacheRepository unreadCountCacheRepository;

	private NotificationWriteService commandService;

	@BeforeEach
	void setUp() {
		commandService = new NotificationWriteService(
			notificationGroupRepository,
			notificationRepository,
			notificationReadStatusRepository,
			idempotencyLookupService,
			notificationWriteExecutor,
			unreadCountCacheRepository
		);
		lenient().when(unreadCountCacheRepository.enabled()).thenReturn(true);
	}

	@Test
	@DisplayName("중복 idempotencyKey 요청이면 기존 그룹을 반환하고 발행을 생략한다")
	void request_returnsExistingGroupWhenIdempotencyKeyAlreadyExists() {
		// given
		SendCommand command = new SendCommand(
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com", "user2@example.com"),
			" idem-order-1001 ",
			null
		);
		NotificationGroup existingGroup = NotificationGroup.create(
			"order-service",
			"idem-order-1001",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			2
		);
		NotificationCommandResult existingResult =
			new NotificationCommandResult(existingGroup.getId(), existingGroup.getTotalCount());

		when(idempotencyLookupService.findExistingResult("order-service", "idem-order-1001"))
			.thenReturn(Optional.of(existingResult));

		// when
		NotificationCommandResult result = commandService.request(command);

		// then
		assertThat(result.groupId()).isEqualTo(existingGroup.getId());
		assertThat(result.totalCount()).isEqualTo(existingGroup.getTotalCount());
		verifyNoInteractions(notificationWriteExecutor);
	}

	@Test
	@DisplayName("신규 idempotencyKey 요청이면 그룹을 생성하고 Outbox 이벤트를 저장한다")
	void request_createsGroupAndSavesOutboxEventsWhenIdempotencyKeyIsNew() {
		// given
		SendCommand command = new SendCommand(
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com", "user2@example.com"),
			"idem-order-2001",
			null
		);

		NotificationCommandResult created = new NotificationCommandResult(1L, 2);
		when(idempotencyLookupService.findExistingResult("order-service", "idem-order-2001"))
			.thenReturn(Optional.empty());
		when(notificationWriteExecutor.createAndPublish(command, "idem-order-2001"))
			.thenReturn(created);

		// when
		NotificationCommandResult result = commandService.request(command);

		// then
		assertThat(result.groupId()).isEqualTo(1L);
		assertThat(result.totalCount()).isEqualTo(2);
		verify(notificationWriteExecutor).createAndPublish(command, "idem-order-2001");
		verify(unreadCountCacheRepository).increment("order-service", "user1@example.com");
		verify(unreadCountCacheRepository).increment("order-service", "user2@example.com");
	}

	@Test
	@DisplayName("idempotencyKey가 비어 있으면 중복 조회 없이 Outbox 이벤트를 저장한다")
	void request_skipsIdempotencyLookupWhenKeyIsBlank() {
		// given
		SendCommand command = new SendCommand(
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com"),
			"   ",
			null
		);
		NotificationCommandResult created = new NotificationCommandResult(10L, 1);
		when(notificationWriteExecutor.createAndPublish(command, null)).thenReturn(created);

		// when
		NotificationCommandResult result = commandService.request(command);

		// then
		assertThat(result.groupId()).isEqualTo(10L);
		assertThat(result.totalCount()).isEqualTo(1);
		verify(idempotencyLookupService, never()).findExistingResult(any(), any());
		verify(idempotencyLookupService, never()).findExistingResultAfterCollision(any(), any());
		verify(notificationWriteExecutor).createAndPublish(command, null);
		verify(unreadCountCacheRepository).increment("order-service", "user1@example.com");
	}

	@Test
	@DisplayName("생성 단계에서 unique 제약 충돌이 나면 기존 그룹을 다시 조회해 반환한다")
	void request_recoversExistingGroupAfterUniqueConstraintCollision() {
		SendCommand command = new SendCommand(
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com", "user2@example.com"),
			"idem-order-3001",
			null
		);
		NotificationGroup existingGroup = NotificationGroup.create(
			"order-service",
			"idem-order-3001",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			2
		);
		NotificationCommandResult existingResult =
			new NotificationCommandResult(existingGroup.getId(), existingGroup.getTotalCount());

		when(idempotencyLookupService.findExistingResult("order-service", "idem-order-3001"))
			.thenReturn(Optional.empty());
		when(idempotencyLookupService.findExistingResultAfterCollision("order-service", "idem-order-3001"))
			.thenReturn(Optional.of(existingResult));
		when(notificationWriteExecutor.createAndPublish(command, "idem-order-3001"))
			.thenThrow(new DataIntegrityViolationException("duplicate key"));

		NotificationCommandResult result = commandService.request(command);

		assertThat(result.groupId()).isEqualTo(existingGroup.getId());
		assertThat(result.totalCount()).isEqualTo(existingGroup.getTotalCount());
		verify(idempotencyLookupService).findExistingResult("order-service", "idem-order-3001");
		verify(idempotencyLookupService)
			.findExistingResultAfterCollision("order-service", "idem-order-3001");
		verify(unreadCountCacheRepository, never()).increment(any(), any());
	}

	@Test
	@DisplayName("생성 충돌 후에도 기존 그룹을 찾지 못하면 예외를 그대로 던진다")
	void request_rethrowsWhenCollisionRecoveryCannotFindExistingGroup() {
		SendCommand command = new SendCommand(
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com", "user2@example.com"),
			"idem-order-4001",
			null
		);
		DataIntegrityViolationException duplicate = new DataIntegrityViolationException("duplicate key");

		when(idempotencyLookupService.findExistingResult("order-service", "idem-order-4001"))
			.thenReturn(Optional.empty());
		when(idempotencyLookupService.findExistingResultAfterCollision("order-service", "idem-order-4001"))
			.thenReturn(Optional.empty());
		when(notificationWriteExecutor.createAndPublish(command, "idem-order-4001")).thenThrow(duplicate);

		assertThatThrownBy(() -> commandService.request(command))
			.isSameAs(duplicate);
		verify(idempotencyLookupService).findExistingResult("order-service", "idem-order-4001");
		verify(idempotencyLookupService)
			.findExistingResultAfterCollision("order-service", "idem-order-4001");
	}

	@Test
	@DisplayName("7일 이내 알림은 읽음 처리한다")
	void markAsRead_marksRecentNotification() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		when(group.getClientId()).thenReturn("clientId");
		Notification notification = org.mockito.Mockito.mock(Notification.class);
		when(notification.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(1));
		when(notification.getGroup()).thenReturn(group);
		when(notification.getReceiver()).thenReturn("receiver@example.com");
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
		when(notificationReadStatusRepository.findReadAtByNotificationId(1L))
			.thenReturn(LocalDateTime.of(2026, 3, 8, 12, 0));

		Optional<NotificationReadResult> result = commandService.markAsRead("clientId", 1L);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().notificationId()).isEqualTo(1L);
		assertThat(result.orElseThrow().readAt()).isEqualTo(LocalDateTime.of(2026, 3, 8, 12, 0));
		verify(notificationReadStatusRepository).markAsRead(eq(1L), any(LocalDateTime.class));
		verify(unreadCountCacheRepository).decrement("clientId", "receiver@example.com");
	}

	@Test
	@DisplayName("7일 지난 알림은 읽음 처리하지 않는다")
	void markAsRead_skipsExpiredNotification() {
		Notification notification = org.mockito.Mockito.mock(Notification.class);
		when(notification.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(8));
		when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

		Optional<NotificationReadResult> result = commandService.markAsRead("clientId", 1L);

		assertThat(result).isEmpty();
		verify(notificationReadStatusRepository, never()).markAsRead(any(Long.class), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("알림이 없으면 읽음 처리하지 않는다")
	void markAsRead_returnsFalseWhenNotificationMissing() {
		when(notificationRepository.findById(1L)).thenReturn(Optional.empty());

		Optional<NotificationReadResult> result = commandService.markAsRead("clientId", 1L);

		assertThat(result).isEmpty();
		verify(notificationReadStatusRepository, never()).markAsRead(any(Long.class), any(LocalDateTime.class));
	}

	@Test
	@DisplayName("그룹 읽음 처리는 최근 7일 그룹의 미읽음 알림만 한 번에 처리한다")
	void markGroupAsRead_marksUnreadNotificationsInGroup() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		Notification first = org.mockito.Mockito.mock(Notification.class);
		Notification second = org.mockito.Mockito.mock(Notification.class);
		when(group.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(1));
		when(group.getClientId()).thenReturn("clientId");
		when(group.getNotifications()).thenReturn(List.of(first, second));
		when(first.getId()).thenReturn(1L);
		when(second.getId()).thenReturn(2L);
		when(first.getReceiver()).thenReturn("a@example.com");
		when(second.getReceiver()).thenReturn("b@example.com");
		when(notificationGroupRepository.findByIdWithNotifications(10L)).thenReturn(Optional.of(group));
		when(notificationReadStatusRepository.markAllAsRead(eq(List.of(1L, 2L)), any(LocalDateTime.class)))
			.thenReturn(2);

		Optional<NotificationGroupReadResult> result = commandService.markGroupAsRead("clientId", 10L);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().groupId()).isEqualTo(10L);
		assertThat(result.orElseThrow().readCount()).isEqualTo(2);
		verify(unreadCountCacheRepository).decrement("clientId", "a@example.com");
		verify(unreadCountCacheRepository).decrement("clientId", "b@example.com");
	}

	@Test
	@DisplayName("그룹의 모든 알림이 이미 읽힘 상태면 새로 읽음 처리된 건수는 0이다")
	void markGroupAsRead_returnsZeroWhenAlreadyRead() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		Notification first = org.mockito.Mockito.mock(Notification.class);
		Notification second = org.mockito.Mockito.mock(Notification.class);
		when(group.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(1));
		when(group.getClientId()).thenReturn("clientId");
		when(group.getNotifications()).thenReturn(List.of(first, second));
		when(first.getId()).thenReturn(1L);
		when(second.getId()).thenReturn(2L);
		when(first.getReceiver()).thenReturn("a@example.com");
		when(second.getReceiver()).thenReturn("b@example.com");
		when(notificationGroupRepository.findByIdWithNotifications(10L)).thenReturn(Optional.of(group));
		when(notificationReadStatusRepository.markAllAsRead(eq(List.of(1L, 2L)), any(LocalDateTime.class)))
			.thenReturn(0);

		Optional<NotificationGroupReadResult> result = commandService.markGroupAsRead("clientId", 10L);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().groupId()).isEqualTo(10L);
		assertThat(result.orElseThrow().readCount()).isZero();
		verify(unreadCountCacheRepository).decrement("clientId", "a@example.com");
		verify(unreadCountCacheRepository).decrement("clientId", "b@example.com");
	}

	@Test
	@DisplayName("7일 지난 그룹은 전체 읽음 처리하지 않는다")
	void markGroupAsRead_skipsExpiredGroup() {
		NotificationGroup group = org.mockito.Mockito.mock(NotificationGroup.class);
		when(group.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(8));
		when(notificationGroupRepository.findByIdWithNotifications(10L)).thenReturn(Optional.of(group));

		Optional<NotificationGroupReadResult> result = commandService.markGroupAsRead("clientId", 10L);

		assertThat(result).isEmpty();
		verify(notificationReadStatusRepository, never()).markAllAsRead(any(), any());
	}

	@Test
	@DisplayName("그룹이 없으면 전체 읽음 처리하지 않는다")
	void markGroupAsRead_returnsEmptyWhenGroupMissing() {
		when(notificationGroupRepository.findByIdWithNotifications(10L)).thenReturn(Optional.empty());

		Optional<NotificationGroupReadResult> result = commandService.markGroupAsRead("clientId", 10L);

		assertThat(result).isEmpty();
		verify(notificationReadStatusRepository, never()).markAllAsRead(any(), any());
	}
}
