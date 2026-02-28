package com.example.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 발송 응답")
public record NotificationSendResponse(
	@Schema(description = "알림 그룹 ID", example = "1")
	Long groupId,

	@Schema(description = "총 발송 대상 수", example = "3")
	int totalCount,

	@Schema(description = "메시지", example = "알림 발송이 요청되었습니다.")
	String message
) {
	public static NotificationSendResponse of(Long groupId, int totalCount) {
		return new NotificationSendResponse(groupId, totalCount, "알림 발송이 요청되었습니다.");
	}
}
