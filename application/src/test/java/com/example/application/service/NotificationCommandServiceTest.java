package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.in.NotificationCommandUseCase.SendCommand;
import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

	@Mock
	private NotificationGroupRepository groupRepository;

	@Mock
	private NotificationEventPublisher eventPublisher;

	@InjectMocks
	private NotificationCommandService commandService;

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
		NotificationGroup result = commandService.request(command);

		// then
		assertThat(result).isSameAs(existingGroup);
		verify(groupRepository, never()).save(any(NotificationGroup.class));
		verifyNoInteractions(eventPublisher);
	}

	@Test
	@DisplayName("신규 idempotencyKey 요청이면 그룹을 생성하고 수신자 수만큼 발행한다")
	void request_createsGroupAndPublishesEventsWhenIdempotencyKeyIsNew() {
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
		NotificationGroup result = commandService.request(command);

		// then
		assertThat(result.getIdempotencyKey()).isEqualTo("idem-order-2001");
		assertThat(result.getNotifications()).hasSize(2);
		verify(groupRepository, times(1)).save(any(NotificationGroup.class));
		verify(eventPublisher, times(2)).publish(nullable(Long.class));
	}

	@Test
	@DisplayName("idempotencyKey가 비어 있으면 중복 조회 없이 발행한다")
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
		NotificationGroup result = commandService.request(command);

		// then
		assertThat(result.getIdempotencyKey()).isNull();
		verify(groupRepository, never()).findByClientIdAndIdempotencyKey(any(), any());
		verify(groupRepository, times(1)).save(any(NotificationGroup.class));
		verify(eventPublisher, times(1)).publish(nullable(Long.class));
	}
}
