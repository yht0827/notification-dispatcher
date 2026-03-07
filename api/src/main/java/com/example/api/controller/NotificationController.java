package com.example.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.request.NotificationGroupQueryRequest;
import com.example.api.dto.request.NotificationListQueryRequest;
import com.example.api.dto.request.NotificationReceiverQueryRequest;
import com.example.api.dto.request.NotificationSendRequest;
import com.example.api.dto.response.ApiResponse;
import com.example.api.dto.response.NotificationGroupDetailResponse;
import com.example.api.dto.response.NotificationGroupSliceResponse;
import com.example.api.dto.response.NotificationListSliceResponse;
import com.example.api.dto.response.NotificationReadResponse;
import com.example.api.dto.response.NotificationResponse;
import com.example.api.dto.response.NotificationSendResponse;
import com.example.api.exception.ErrorCode;
import com.example.api.exception.NotificationException;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.in.NotificationWriteUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Notification", description = "알림 발송 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

	private final NotificationWriteUseCase writeUseCase;
	private final NotificationQueryUseCase queryUseCase;

	@Operation(summary = "알림 발송", description = "단일 또는 대량 알림을 발송합니다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<NotificationSendResponse> send(@Valid @RequestBody NotificationSendRequest request) {
		SendCommand command = new SendCommand(
			request.clientId(),
			request.sender(),
			request.title(),
			request.content(),
			request.channelType(),
			request.receivers(),
			request.idempotencyKey()
		);

		NotificationCommandResult result = writeUseCase.request(command);
		return ApiResponse.ok(NotificationSendResponse.of(result.groupId(), result.totalCount()));
	}

	@Operation(summary = "알림 그룹 조회")
	@GetMapping("/groups/{groupId}")
	public ApiResponse<NotificationGroupDetailResponse> getGroup(@PathVariable Long groupId) {
		return queryUseCase.getGroupDetail(groupId)
			.map(NotificationGroupDetailResponse::from)
			.map(ApiResponse::ok)
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_GROUP_NOT_FOUND));
	}

	@Operation(summary = "요청자별 알림 그룹 목록 조회 (최근 7일, 커서 페이징)")
	@GetMapping("/groups")
	public ApiResponse<NotificationGroupSliceResponse> getGroupsByClientId(
		@Valid @ModelAttribute NotificationGroupQueryRequest request) {
		return ApiResponse.ok(
			NotificationGroupSliceResponse.from(
				queryUseCase.getGroupsByClientId(request.clientId(), request.cursorId(), request.resolveSize())
			)
		);
	}

	@Operation(summary = "알림 묶음 목록 조회")
	@GetMapping
	public ApiResponse<NotificationListSliceResponse> getNotificationBundles(
		@Valid @ModelAttribute NotificationListQueryRequest request) {
		return ApiResponse.ok(
			NotificationListSliceResponse.from(
				queryUseCase.getRecentGroups(request.cursorId(), request.resolveSize())
			)
		);
	}

	@Operation(summary = "개별 알림 조회")
	@GetMapping("/{notificationId}")
	public ApiResponse<NotificationResponse> getNotification(@PathVariable Long notificationId) {
		return queryUseCase.getNotification(notificationId)
			.map(NotificationResponse::from)
			.map(ApiResponse::ok)
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
	}

	@Operation(summary = "알림 읽음 처리")
	@PatchMapping("/{notificationId}/read")
	public ApiResponse<NotificationReadResponse> markAsRead(@PathVariable Long notificationId) {
		if (!writeUseCase.markAsRead(notificationId)) {
			throw new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND);
		}
		return ApiResponse.ok(NotificationReadResponse.of(notificationId));
	}

	@Operation(summary = "수신자별 알림 목록 조회")
	@GetMapping(params = "receiver")
	public ApiResponse<List<NotificationResponse>> getNotificationsByReceiver(
		@Valid @ModelAttribute NotificationReceiverQueryRequest request) {
		List<NotificationResponse> responses = queryUseCase.getNotificationsByReceiver(request.receiver())
			.stream()
			.map(NotificationResponse::from)
			.toList();
		return ApiResponse.ok(responses);
	}
}
