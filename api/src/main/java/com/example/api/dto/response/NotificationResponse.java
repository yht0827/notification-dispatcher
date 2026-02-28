package com.example.api.dto.response;

import java.time.LocalDateTime;
import java.util.Optional;

import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;
import com.example.domain.notification.NotificationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "개별 알림 조회 응답")
public record NotificationResponse(
	@Schema(description = "알림 ID", example = "1")
	Long id,

	@Schema(description = "그룹 ID", example = "1")
	Long groupId,

	@Schema(description = "수신자", example = "user@email.com")
	String receiver,

	@Schema(description = "발신자명", example = "MyShop")
	String sender,

	@Schema(description = "제목", example = "주문 완료")
	String title,

	@Schema(description = "채널 타입", example = "EMAIL")
	ChannelType channelType,

	@Schema(description = "상태", example = "SENT")
	NotificationStatus status,

	@Schema(description = "발송 시각")
	LocalDateTime sentAt,

	@Schema(description = "실패 사유", example = "Invalid email address")
	String failReason,

	@Schema(description = "생성일시")
	LocalDateTime createdAt
) {
	public static NotificationResponse from(Notification notification) {
		var group = Optional.ofNullable(notification.getGroup());

		return new NotificationResponse(
			notification.getId(),
			group.map(NotificationGroup::getId).orElse(null),
			notification.getReceiver(),
			group.map(NotificationGroup::getSender).orElse(null),
			group.map(NotificationGroup::getTitle).orElse(null),
			group.map(NotificationGroup::getChannelType).orElse(null),
			notification.getStatus(),
			notification.getSentAt(),
			notification.getFailReason(),
			notification.getCreatedAt()
		);
	}
}
