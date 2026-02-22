package com.example.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.request.NotificationSendRequest;
import com.example.api.dto.response.NotificationGroupResponse;
import com.example.api.dto.response.NotificationResponse;
import com.example.api.dto.response.NotificationSendResponse;
import com.example.api.exception.ErrorCode;
import com.example.api.exception.NotificationException;
import com.example.application.port.in.NotificationUseCase;
import com.example.application.port.in.NotificationUseCase.SendCommand;
import com.example.common.response.ApiResponse;
import com.example.domain.notification.NotificationGroup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Notification", description = "알림 발송 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationUseCase notificationUseCase;

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
			request.receivers()
		);

		NotificationGroup group = notificationUseCase.send(command);
		return ApiResponse.ok(NotificationSendResponse.of(group.getId(), group.getTotalCount()));
	}

	@Operation(summary = "알림 그룹 조회")
	@GetMapping("/groups/{groupId}")
	public ApiResponse<NotificationGroupResponse> getGroup(@PathVariable Long groupId) {
		return notificationUseCase.getGroup(groupId)
			.map(NotificationGroupResponse::from)
			.map(ApiResponse::ok)
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_GROUP_NOT_FOUND));
	}

	@Operation(summary = "클라이언트별 알림 그룹 목록 조회")
	@GetMapping("/groups")
	public ApiResponse<List<NotificationGroupResponse>> getGroupsByClientId(@RequestParam("clientId") String clientId) {
		List<NotificationGroupResponse> responses = notificationUseCase.getGroupsByClientId(clientId)
			.stream()
			.map(NotificationGroupResponse::from)
			.toList();
		return ApiResponse.ok(responses);
	}

	@Operation(summary = "개별 알림 조회")
	@GetMapping("/{notificationId}")
	public ApiResponse<NotificationResponse> getNotification(@PathVariable Long notificationId) {
		return notificationUseCase.getNotification(notificationId)
			.map(NotificationResponse::from)
			.map(ApiResponse::ok)
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
	}

	@Operation(summary = "수신자별 알림 목록 조회")
	@GetMapping
	public ApiResponse<List<NotificationResponse>> getNotificationsByReceiver(
		@RequestParam("receiver") String receiver) {
		List<NotificationResponse> responses = notificationUseCase.getNotificationsByReceiver(receiver)
			.stream()
			.map(NotificationResponse::from)
			.toList();
		return ApiResponse.ok(responses);
	}
}
