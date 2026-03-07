package com.example.api.dto.response;

import java.time.LocalDateTime;

import com.example.application.port.in.result.NotificationGroupResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 그룹 조회 응답")
public record NotificationGroupResponse(
	@Schema(description = "그룹 ID", example = "1")
	Long id,

	@Schema(description = "클라이언트 ID", example = "order-service")
	String clientId,

	@Schema(description = "발신자명", example = "MyShop")
	String sender,

	@Schema(description = "제목", example = "주문 완료")
	String title,

	@Schema(description = "그룹 타입", example = "BULK")
	GroupType groupType,

	@Schema(description = "채널 타입", example = "EMAIL")
	ChannelType channelType,

	@Schema(description = "총 발송 대상 수", example = "100")
	int totalCount,

	@Schema(description = "발송 성공 수", example = "95")
	int sentCount,

	@Schema(description = "발송 실패 수", example = "5")
	int failedCount,

	@Schema(description = "대기 중 수", example = "0")
	int pendingCount,

	@Schema(description = "완료 여부", example = "true")
	boolean completed,

	@Schema(description = "생성일시")
	LocalDateTime createdAt
) {
	public static NotificationGroupResponse from(NotificationGroupResult group) {
		return new NotificationGroupResponse(
			group.id(),
			group.clientId(),
			group.sender(),
			group.title(),
			group.groupType(),
			group.channelType(),
			group.totalCount(),
			group.sentCount(),
			group.failedCount(),
			group.pendingCount(),
			group.completed(),
			group.createdAt()
		);
	}
}
