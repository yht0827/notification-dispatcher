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
import com.example.application.port.in.NotificationCommandUseCase;
import com.example.application.port.in.NotificationCommandUseCase.SendCommand;
import com.example.application.port.in.NotificationQueryUseCase;
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

	private final NotificationCommandUseCase commandUseCase;
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
			request.receivers()
		);

		NotificationGroup group = commandUseCase.send(command);
		return ApiResponse.ok(NotificationSendResponse.of(group.getId(), group.getTotalCount()));
	}

	@Operation(summary = "알림 그룹 조회")
	@GetMapping("/groups/{groupId}")
	public ApiResponse<NotificationGroupResponse> getGroup(@PathVariable Long groupId) {
		return queryUseCase.getGroup(groupId)
			.map(NotificationGroupResponse::from)
			.map(ApiResponse::ok)
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_GROUP_NOT_FOUND));
	}

	@Operation(summary = "클라이언트별 알림 그룹 목록 조회")
	@GetMapping("/groups")
	public ApiResponse<List<NotificationGroupResponse>> getGroupsByClientId(@RequestParam("clientId") String clientId) {
		List<NotificationGroupResponse> responses = queryUseCase.getGroupsByClientId(clientId)
			.stream()
			.map(NotificationGroupResponse::from)
			.toList();
		return ApiResponse.ok(responses);
	}

	@Operation(summary = "개별 알림 조회")
	@GetMapping("/{notificationId}")
	public ApiResponse<NotificationResponse> getNotification(@PathVariable Long notificationId) {
		return queryUseCase.getNotification(notificationId)
			.map(NotificationResponse::from)
			.map(ApiResponse::ok)
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
	}

	@Operation(summary = "수신자별 알림 목록 조회")
	@GetMapping
	public ApiResponse<List<NotificationResponse>> getNotificationsByReceiver(
		@RequestParam("receiver") String receiver) {
		List<NotificationResponse> responses = queryUseCase.getNotificationsByReceiver(receiver)
			.stream()
			.map(NotificationResponse::from)
			.toList();
		return ApiResponse.ok(responses);
	}
}
