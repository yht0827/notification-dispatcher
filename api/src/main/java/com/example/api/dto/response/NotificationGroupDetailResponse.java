package com.example.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 그룹 상세 조회 응답")
public record NotificationGroupDetailResponse(
	@Schema(description = "그룹 ID", example = "1")
	Long groupId,

	@Schema(description = "클라이언트 ID", example = "order-service")
	String clientId,

	@Schema(description = "발신자명", example = "MyShop")
	String sender,

	@Schema(description = "제목", example = "주문 완료")
	String title,

	@Schema(description = "내용", example = "주문이 정상적으로 접수되었습니다.")
	String content,

	@Schema(description = "그룹 타입", example = "BULK")
	GroupType groupType,

	@Schema(description = "채널 타입", example = "EMAIL")
	ChannelType channelType,

	@Schema(description = "총 발송 대상 수", example = "3")
	int totalCount,

	@Schema(description = "발송 성공 수", example = "2")
	int sentCount,

	@Schema(description = "발송 실패 수", example = "1")
	int failedCount,

	@Schema(description = "대기 중 수", example = "0")
	int pendingCount,

	@Schema(description = "완료 여부", example = "true")
	boolean completed,

	@Schema(description = "생성일시")
	LocalDateTime createdAt,

	@Schema(description = "그룹 내 알림 목록")
	List<NotificationItemResponse> notifications
) {
	public static NotificationGroupDetailResponse from(NotificationGroup group) {
		List<NotificationItemResponse> notifications = group.getNotifications().stream()
			.map(NotificationItemResponse::from)
			.toList();

		return new NotificationGroupDetailResponse(
			group.getId(),
			group.getClientId(),
			group.getSender(),
			group.getTitle(),
			group.getContent(),
			group.getGroupType(),
			group.getChannelType(),
			group.getTotalCount(),
			group.getSentCount(),
			group.getFailedCount(),
			group.getPendingCount(),
			group.isCompleted(),
			group.getCreatedAt(),
			notifications
		);
	}

	@Schema(description = "그룹 내 개별 알림 정보")
	public record NotificationItemResponse(
		@Schema(description = "알림 ID", example = "101")
		Long notificationId,

		@Schema(description = "수신자", example = "user@email.com")
		String receiver,

		@Schema(description = "알림 상태", example = "SENT")
		NotificationStatus status,

		@Schema(description = "발송 시각")
		LocalDateTime sentAt,

		@Schema(description = "실패 사유", example = "Invalid email address")
		String failReason,

		@Schema(description = "생성일시")
		LocalDateTime createdAt
	) {
		public static NotificationItemResponse from(Notification notification) {
			return new NotificationItemResponse(
				notification.getId(),
				notification.getReceiver(),
				notification.getStatus(),
				notification.getSentAt(),
				notification.getFailReason(),
				notification.getCreatedAt()
			);
		}
	}
}
