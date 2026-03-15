package com.example.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.api.dto.request.NotificationGroupQueryRequest;
import com.example.api.dto.request.NotificationSendRequest;
import com.example.api.dto.response.ApiResponse;
import com.example.api.dto.response.NotificationGroupDetailResponse;
import com.example.api.dto.response.NotificationGroupReadResponse;
import com.example.api.dto.response.NotificationGroupSliceResponse;
import com.example.api.dto.response.NotificationReadResponse;
import com.example.api.dto.response.NotificationResponse;
import com.example.api.dto.response.NotificationSendResponse;
import com.example.api.dto.response.NotificationUnreadCountResponse;
import com.example.api.exception.ErrorCode;
import com.example.api.exception.NotificationException;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.in.NotificationWriteUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupReadResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationItemResult;
import com.example.application.port.in.result.NotificationReadResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.application.port.in.result.NotificationUnreadCountResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationStatus;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

	@Mock
	private NotificationWriteUseCase writeUseCase;

	@Mock
	private NotificationQueryUseCase queryUseCase;

	@InjectMocks
	private NotificationController controller;

	@Test
	@DisplayName("발송 요청 시 ApiResponse와 NotificationSendResponse를 반환한다")
	void send_returnsResponse() {
		NotificationSendRequest request = new NotificationSendRequest(
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com", "user2@example.com"),
			"idem-1",
			null
		);
		when(writeUseCase.request(any(SendCommand.class)))
			.thenReturn(new NotificationCommandResult(1L, 2));

		ApiResponse<NotificationSendResponse> response = controller.send("order-service", request);

		assertThat(response.success()).isTrue();
		assertThat(response.data().groupId()).isEqualTo(1L);
		assertThat(response.data().totalCount()).isEqualTo(2);
		assertThat(response.data().message()).isEqualTo("알림 발송이 요청되었습니다.");
		verify(writeUseCase).request(any(SendCommand.class));
	}

	@Test
	@DisplayName("그룹 상세 조회 성공 시 NotificationGroupDetailResponse를 반환한다")
	void getGroup_returnsDetailResponse() {
		NotificationGroupDetailResult result = new NotificationGroupDetailResult(
			1L,
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			GroupType.BULK,
			ChannelType.EMAIL,
			2,
			1,
			0,
			1,
			false,
			LocalDateTime.now(),
			List.of(new NotificationItemResult(
				101L,
				"user1@example.com",
				NotificationStatus.PENDING,
				null,
				null,
				LocalDateTime.now(),
				true,
				LocalDateTime.of(2026, 3, 8, 12, 0)
			))
		);
		when(queryUseCase.getGroupDetail(1L)).thenReturn(Optional.of(result));

		ApiResponse<NotificationGroupDetailResponse> response = controller.getGroup(1L);

		assertThat(response.success()).isTrue();
		assertThat(response.data().groupId()).isEqualTo(1L);
		assertThat(response.data().notifications()).hasSize(1);
		assertThat(response.data().notifications().getFirst().isRead()).isTrue();
		assertThat(response.data().notifications().getFirst().readAt())
			.isEqualTo(LocalDateTime.of(2026, 3, 8, 12, 0));
	}

	@Test
	@DisplayName("그룹 상세 조회 실패 시 NotificationException을 던진다")
	void getGroup_throwsWhenNotFound() {
		when(queryUseCase.getGroupDetail(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.getGroup(1L))
			.isInstanceOf(NotificationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOTIFICATION_GROUP_NOT_FOUND);
	}

	@Test
	@DisplayName("요청자별 그룹 목록 조회는 기본 size 20을 사용한다")
	void getGroupsByClientId_usesDefaultSize() {
		NotificationGroupQueryRequest request = new NotificationGroupQueryRequest(null, null, null);
		CursorSlice<NotificationGroupResult> slice = new CursorSlice<>(
			List.of(new NotificationGroupResult(
				1L,
				"order-service",
				"MyShop",
				"주문 완료",
				GroupType.SINGLE,
				ChannelType.EMAIL,
				1,
				1,
				0,
				0,
				true,
				LocalDateTime.now()
			)),
			false,
			null
		);
		when(queryUseCase.getGroupsByClientId("order-service", null, 20, null)).thenReturn(slice);

		ApiResponse<NotificationGroupSliceResponse> response = controller.getGroupsByClientId("order-service", request);

		assertThat(response.success()).isTrue();
		assertThat(response.data().items()).hasSize(1);
		verify(queryUseCase).getGroupsByClientId("order-service", null, 20, null);
	}

	@Test
	@DisplayName("개별 알림 조회 성공 시 NotificationResponse를 반환한다")
	void getNotification_returnsResponse() {
		NotificationResult result = new NotificationResult(
			1L,
			10L,
			"user@example.com",
			"MyShop",
			"주문 완료",
			ChannelType.EMAIL,
			NotificationStatus.SENT,
			LocalDateTime.now(),
			null,
			LocalDateTime.now(),
			true,
			LocalDateTime.of(2026, 3, 8, 12, 0)
		);
		when(queryUseCase.getNotification(1L)).thenReturn(Optional.of(result));

		ApiResponse<NotificationResponse> response = controller.getNotification(1L);

		assertThat(response.success()).isTrue();
		assertThat(response.data().id()).isEqualTo(1L);
		assertThat(response.data().groupId()).isEqualTo(10L);
		assertThat(response.data().isRead()).isTrue();
		assertThat(response.data().readAt()).isEqualTo(LocalDateTime.of(2026, 3, 8, 12, 0));
	}

	@Test
	@DisplayName("개별 알림 조회 실패 시 NotificationException을 던진다")
	void getNotification_throwsWhenNotFound() {
		when(queryUseCase.getNotification(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.getNotification(1L))
			.isInstanceOf(NotificationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
	}

	@Test
	@DisplayName("읽지 않은 알림 개수 조회 성공 시 count 응답을 반환한다")
	void getUnreadCount_returnsResponse() {
		when(queryUseCase.getUnreadCount("dev-api-key-001", "user@example.com"))
			.thenReturn(new NotificationUnreadCountResult("user@example.com", 12L));

		ApiResponse<NotificationUnreadCountResponse> response =
			controller.getUnreadCount("dev-api-key-001", "user@example.com");

		assertThat(response.success()).isTrue();
		assertThat(response.data()).isNotNull();
		assertThat(response.data().receiver()).isEqualTo("user@example.com");
		assertThat(response.data().unreadCount()).isEqualTo(12L);
	}

	@Test
	@DisplayName("읽음 처리 성공 시 success 응답을 반환한다")
	void markAsRead_returnsSuccess() {
		when(writeUseCase.markAsRead("dev-api-key-001", 1L))
			.thenReturn(Optional.of(new NotificationReadResult(1L, LocalDateTime.of(2026, 3, 8, 12, 0))));

		ApiResponse<NotificationReadResponse> response = controller.markAsRead("dev-api-key-001", 1L);

		assertThat(response.success()).isTrue();
		assertThat(response.data()).isNotNull();
		assertThat(response.data().notificationId()).isEqualTo(1L);
		assertThat(response.data().readAt()).isEqualTo(LocalDateTime.of(2026, 3, 8, 12, 0));
		assertThat(response.data().message()).isEqualTo("알림을 읽음 처리했습니다.");
		verify(writeUseCase).markAsRead("dev-api-key-001", 1L);
	}

	@Test
	@DisplayName("읽음 처리 대상이 없으면 NotificationException을 던진다")
	void markAsRead_throwsWhenNotFound() {
		when(writeUseCase.markAsRead("dev-api-key-001", 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.markAsRead("dev-api-key-001", 1L))
			.isInstanceOf(NotificationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
	}

	@Test
	@DisplayName("그룹 읽음 처리 성공 시 success 응답을 반환한다")
	void markGroupAsRead_returnsSuccess() {
		when(writeUseCase.markGroupAsRead("dev-api-key-001", 10L))
			.thenReturn(Optional.of(new NotificationGroupReadResult(10L, 2, LocalDateTime.of(2026, 3, 8, 12, 0))));

		ApiResponse<NotificationGroupReadResponse> response = controller.markGroupAsRead("dev-api-key-001", 10L);

		assertThat(response.success()).isTrue();
		assertThat(response.data()).isNotNull();
		assertThat(response.data().groupId()).isEqualTo(10L);
		assertThat(response.data().readCount()).isEqualTo(2);
		assertThat(response.data().readAt()).isEqualTo(LocalDateTime.of(2026, 3, 8, 12, 0));
		assertThat(response.data().message()).isEqualTo("알림 그룹을 읽음 처리했습니다.");
	}

	@Test
	@DisplayName("그룹 읽음 처리 대상이 없으면 NotificationException을 던진다")
	void markGroupAsRead_throwsWhenNotFound() {
		when(writeUseCase.markGroupAsRead("dev-api-key-001", 10L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.markGroupAsRead("dev-api-key-001", 10L))
			.isInstanceOf(NotificationException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.NOTIFICATION_GROUP_NOT_FOUND);
	}

}
