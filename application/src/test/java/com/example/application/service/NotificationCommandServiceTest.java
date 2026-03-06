package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.application.mapper.NotificationCommandResultMapper;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

	@Mock
	private NotificationGroupRepository groupRepository;

	@Mock
	private OutboxRepository outboxRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Spy
	private NotificationCommandResultMapper resultMapper;

	private NotificationCommandService commandService;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		commandService = new NotificationCommandService(
			groupRepository,
			outboxRepository,
			eventPublisher,
			resultMapper,
			transactionManager
		);
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
			" idem-order-1001 "
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

		when(groupRepository.findByClientIdAndIdempotencyKey("order-service", "idem-order-1001"))
			.thenReturn(Optional.of(existingGroup));

		// when
		NotificationCommandResult result = commandService.request(command);

		// then
		assertThat(result.groupId()).isEqualTo(existingGroup.getId());
		assertThat(result.totalCount()).isEqualTo(existingGroup.getTotalCount());
		verify(groupRepository, never()).save(any(NotificationGroup.class));
		verifyNoInteractions(outboxRepository);
		verifyNoInteractions(eventPublisher);
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
			"idem-order-2001"
		);

		when(groupRepository.findByClientIdAndIdempotencyKey("order-service", "idem-order-2001"))
			.thenReturn(Optional.empty());
		when(groupRepository.save(any(NotificationGroup.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		// when
		NotificationCommandResult result = commandService.request(command);

		// then
		assertThat(result.totalCount()).isEqualTo(2);
		verify(groupRepository, times(1)).save(argThat(group ->
			"idem-order-2001".equals(group.getIdempotencyKey())
				&& group.getNotifications().size() == 2));
		verify(outboxRepository, times(1)).saveAll(argThat(outboxes -> outboxes.size() == 2));
		verify(eventPublisher, times(1)).publishEvent(any(OutboxSavedEvent.class));
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
			"   "
		);
		when(groupRepository.save(any(NotificationGroup.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		// when
		NotificationCommandResult result = commandService.request(command);

		// then
		assertThat(result.totalCount()).isEqualTo(1);
		verify(groupRepository, never()).findByClientIdAndIdempotencyKey(any(), any());
		verify(groupRepository, times(1)).save(argThat(group ->
			group.getIdempotencyKey() == null
				&& group.getNotifications().size() == 1));
		verify(outboxRepository, times(1)).saveAll(argThat(outboxes -> outboxes.size() == 1));
		verify(eventPublisher, times(1)).publishEvent(any(OutboxSavedEvent.class));
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
			"idem-order-3001"
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

		when(groupRepository.findByClientIdAndIdempotencyKey("order-service", "idem-order-3001"))
			.thenReturn(Optional.empty(), Optional.of(existingGroup));
		when(groupRepository.save(any(NotificationGroup.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate key"));

		NotificationCommandResult result = commandService.request(command);

		assertThat(result.groupId()).isEqualTo(existingGroup.getId());
		assertThat(result.totalCount()).isEqualTo(existingGroup.getTotalCount());
		verify(groupRepository, times(2))
			.findByClientIdAndIdempotencyKey("order-service", "idem-order-3001");
		verify(outboxRepository, never()).saveAll(any());
		verifyNoInteractions(eventPublisher);
		verify(transactionManager, times(1)).rollback(any());
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
			"idem-order-4001"
		);
		DataIntegrityViolationException duplicate = new DataIntegrityViolationException("duplicate key");

		when(groupRepository.findByClientIdAndIdempotencyKey("order-service", "idem-order-4001"))
			.thenReturn(Optional.empty(), Optional.empty());
		when(groupRepository.save(any(NotificationGroup.class))).thenThrow(duplicate);

		assertThatThrownBy(() -> commandService.request(command))
			.isSameAs(duplicate);
		verify(outboxRepository, never()).saveAll(any());
		verifyNoInteractions(eventPublisher);
		verify(transactionManager, times(1)).rollback(any());
	}
}
