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
import com.example.api.dto.request.NotificationListQueryRequest;
import com.example.api.dto.request.NotificationReceiverQueryRequest;
import com.example.api.dto.request.NotificationSendRequest;
import com.example.api.dto.response.ApiResponse;
import com.example.api.dto.response.NotificationGroupDetailResponse;
import com.example.api.dto.response.NotificationGroupSliceResponse;
import com.example.api.dto.response.NotificationListSliceResponse;
import com.example.api.dto.response.NotificationResponse;
import com.example.api.dto.response.NotificationSendResponse;
import com.example.api.exception.ErrorCode;
import com.example.api.exception.NotificationException;
import com.example.application.port.in.NotificationCommandUseCase;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationItemResult;
import com.example.application.port.in.result.NotificationListResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationStatus;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

	@Mock
	private NotificationCommandUseCase commandUseCase;

	@Mock
	private NotificationQueryUseCase queryUseCase;

	@InjectMocks
	private NotificationController controller;

	@Test
	@DisplayName("발송 요청 시 ApiResponse와 NotificationSendResponse를 반환한다")
	void send_returnsResponse() {
		NotificationSendRequest request = new NotificationSendRequest(
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 완료되었습니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com", "user2@example.com"),
			"idem-1"
		);
		when(commandUseCase.request(any(SendCommand.class)))
			.thenReturn(new NotificationCommandResult(1L, 2));

		ApiResponse<NotificationSendResponse> response = controller.send(request);

		assertThat(response.success()).isTrue();
		assertThat(response.data().groupId()).isEqualTo(1L);
		assertThat(response.data().totalCount()).isEqualTo(2);
		assertThat(response.data().message()).isEqualTo("알림 발송이 요청되었습니다.");
		verify(commandUseCase).request(any(SendCommand.class));
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
				LocalDateTime.now()
			))
		);
		when(queryUseCase.getGroupDetail(1L)).thenReturn(Optional.of(result));

		ApiResponse<NotificationGroupDetailResponse> response = controller.getGroup(1L);

		assertThat(response.success()).isTrue();
		assertThat(response.data().groupId()).isEqualTo(1L);
		assertThat(response.data().notifications()).hasSize(1);
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
		NotificationGroupQueryRequest request = new NotificationGroupQueryRequest("order-service", null, null);
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
		when(queryUseCase.getGroupsByClientId("order-service", null, 20)).thenReturn(slice);

		ApiResponse<NotificationGroupSliceResponse> response = controller.getGroupsByClientId(request);

		assertThat(response.success()).isTrue();
		assertThat(response.data().items()).hasSize(1);
		verify(queryUseCase).getGroupsByClientId("order-service", null, 20);
	}

	@Test
	@DisplayName("알림 묶음 목록 조회는 요청 size를 사용한다")
	void getNotificationBundles_returnsSliceResponse() {
		NotificationListQueryRequest request = new NotificationListQueryRequest(50L, 10);
		CursorSlice<NotificationListResult> slice = new CursorSlice<>(
			List.of(new NotificationListResult(
				1L,
				"속보",
				"브리핑",
				LocalDateTime.now(),
				3,
				2
			)),
			true,
			1L
		);
		when(queryUseCase.getRecentGroups(50L, 10)).thenReturn(slice);

		ApiResponse<NotificationListSliceResponse> response = controller.getNotificationBundles(request);

		assertThat(response.success()).isTrue();
		assertThat(response.data().items()).hasSize(1);
		assertThat(response.data().hasNext()).isTrue();
		verify(queryUseCase).getRecentGroups(50L, 10);
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
			LocalDateTime.now()
		);
		when(queryUseCase.getNotification(1L)).thenReturn(Optional.of(result));

		ApiResponse<NotificationResponse> response = controller.getNotification(1L);

		assertThat(response.success()).isTrue();
		assertThat(response.data().id()).isEqualTo(1L);
		assertThat(response.data().groupId()).isEqualTo(10L);
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
	@DisplayName("수신자별 조회는 NotificationResponse 목록을 반환한다")
	void getNotificationsByReceiver_returnsResponseList() {
		NotificationReceiverQueryRequest request = new NotificationReceiverQueryRequest("user@example.com");
		when(queryUseCase.getNotificationsByReceiver("user@example.com"))
			.thenReturn(List.of(new NotificationResult(
				1L,
				10L,
				"user@example.com",
				"MyShop",
				"주문 완료",
				ChannelType.SMS,
				NotificationStatus.SENT,
				LocalDateTime.now(),
				null,
				LocalDateTime.now()
			)));

		ApiResponse<List<NotificationResponse>> response = controller.getNotificationsByReceiver(request);

		assertThat(response.success()).isTrue();
		assertThat(response.data()).hasSize(1);
		assertThat(response.data().getFirst().receiver()).isEqualTo("user@example.com");
		verify(queryUseCase).getNotificationsByReceiver("user@example.com");
	}
}
