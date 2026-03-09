package com.example.api.dto.response;

import com.example.application.port.in.result.NotificationUnreadCountResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "읽지 않은 알림 개수 조회 응답")
public record NotificationUnreadCountResponse(
	@Schema(description = "수신자", example = "user@example.com")
	String receiver,

	@Schema(description = "읽지 않은 알림 개수", example = "12")
	long unreadCount
) {
	public static NotificationUnreadCountResponse from(NotificationUnreadCountResult result) {
		return new NotificationUnreadCountResponse(result.receiver(), result.unreadCount());
	}
}
