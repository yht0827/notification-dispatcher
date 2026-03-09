package com.example.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.auth.ApiKeyAuthFilter;
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
import com.example.application.port.in.result.NotificationCommandResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
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

	@Operation(summary = "개별 알림 조회")
	@GetMapping("/{notificationId}")
	public ApiResponse<NotificationResponse> getNotification(@PathVariable Long notificationId) {
		return queryUseCase.getNotification(notificationId)
			.map(NotificationResponse::from)
			.map(ApiResponse::ok)
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
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
		@RequestHeader(ApiKeyAuthFilter.HEADER_API_KEY) String clientId,
		@Valid @ModelAttribute NotificationGroupQueryRequest request) {
		return ApiResponse.ok(
			NotificationGroupSliceResponse.from(
				queryUseCase.getGroupsByClientId(clientId, request.cursorId(), request.resolveSize())
			)
		);
	}

	@Operation(summary = "수신자별 읽지 않은 알림 개수 조회 (최근 7일)")
	@GetMapping("/unread-count")
	public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(
		@RequestHeader(ApiKeyAuthFilter.HEADER_API_KEY) String clientId,
		@RequestParam @NotBlank(message = "receiver는 비어 있을 수 없습니다.") String receiver) {
		return ApiResponse.ok(NotificationUnreadCountResponse.from(
			queryUseCase.getUnreadCount(clientId, receiver)
		));
	}

	@Operation(summary = "알림 발송", description = "단일 또는 대량 알림을 발송합니다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<NotificationSendResponse> send(
		@RequestHeader(ApiKeyAuthFilter.HEADER_API_KEY) String clientId,
		@Valid @RequestBody NotificationSendRequest request) {
		NotificationCommandResult result = writeUseCase.request(request.toCommand(clientId));
		return ApiResponse.ok(NotificationSendResponse.of(result.groupId(), result.totalCount()));
	}

	@Operation(summary = "알림 읽음 처리")
	@PatchMapping("/{notificationId}/read")
	public ApiResponse<NotificationReadResponse> markAsRead(
		@RequestHeader(ApiKeyAuthFilter.HEADER_API_KEY) String clientId,
		@PathVariable Long notificationId) {
		return writeUseCase.markAsRead(clientId, notificationId)
			.map(result -> ApiResponse.ok(NotificationReadResponse.of(result.notificationId(), result.readAt())))
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));
	}

	@Operation(summary = "알림 그룹 전체 읽음 처리")
	@PatchMapping("/groups/{groupId}/read")
	public ApiResponse<NotificationGroupReadResponse> markGroupAsRead(
		@RequestHeader(ApiKeyAuthFilter.HEADER_API_KEY) String clientId,
		@PathVariable Long groupId) {
		return writeUseCase.markGroupAsRead(clientId, groupId)
			.map(result -> ApiResponse.ok(
				NotificationGroupReadResponse.of(result.groupId(), result.readCount(), result.readAt())))
			.orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_GROUP_NOT_FOUND));
	}
}
