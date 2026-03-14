package com.example.api.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 읽음 처리 응답")
public record NotificationReadResponse(
	@Schema(description = "알림 ID", example = "1")
	Long notificationId,

	@Schema(description = "읽음 시각")
	LocalDateTime readAt,

	@Schema(description = "메시지", example = "알림을 읽음 처리했습니다.")
	String message
) {
	public static NotificationReadResponse of(Long notificationId, LocalDateTime readAt) {
		return new NotificationReadResponse(notificationId, readAt, "알림을 읽음 처리했습니다.");
	}
}
