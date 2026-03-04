package com.example.api.dto.response;

import java.time.LocalDateTime;

import com.example.application.port.in.result.NotificationListResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 묶음 목록 응답")
public record NotificationListResponse(
	@Schema(description = "그룹 ID", example = "1")
	Long groupId,

	@Schema(description = "대표 제목", example = "삼성전자 잠정실적 발표")
	String title,

	@Schema(description = "대표 내용", example = "4분기 영업이익 20조원...")
	String content,

	@Schema(description = "생성일시")
	LocalDateTime createdAt,

	@Schema(description = "전체 알림 수", example = "3")
	int totalCount,

	@Schema(description = "더보기 알림 수", example = "2")
	int moreCount
) {
	public static NotificationListResponse from(NotificationListResult group) {
		return new NotificationListResponse(
			group.groupId(),
			group.title(),
			group.content(),
			group.createdAt(),
			group.totalCount(),
			group.moreCount()
		);
	}
}
