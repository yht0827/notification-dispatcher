package com.example.api.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 그룹 읽음 처리 응답")
public record NotificationGroupReadResponse(
	@Schema(description = "알림 그룹 ID", example = "1")
	Long groupId,

	@Schema(description = "이번 요청에서 새로 읽음 처리된 알림 수", example = "3")
	int readCount,

	@Schema(description = "이번 그룹 읽음 요청 처리 시각")
	LocalDateTime readAt,

	@Schema(description = "메시지", example = "알림 그룹을 읽음 처리했습니다.")
	String message
) {
	public static NotificationGroupReadResponse of(Long groupId, int readCount, LocalDateTime readAt) {
		return new NotificationGroupReadResponse(groupId, readCount, readAt, "알림 그룹을 읽음 처리했습니다.");
	}
}
