package com.example.api.dto.request;

import java.util.List;

import com.example.domain.notification.ChannelType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 발송 요청")
public record NotificationSendRequest(
	@Schema(description = "클라이언트 ID", example = "order-service")
	@NotBlank(message = "clientId는 필수입니다")
	String clientId,

	@Schema(description = "발신자명", example = "MyShop")
	@NotBlank(message = "sender는 필수입니다")
	String sender,

	@Schema(description = "알림 제목", example = "주문 완료")
	@NotBlank(message = "title은 필수입니다")
	String title,

	@Schema(description = "알림 내용", example = "주문이 완료되었습니다.")
	@NotBlank(message = "content는 필수입니다")
	String content,

	@Schema(description = "발송 채널", example = "EMAIL")
	@NotNull(message = "channelType은 필수입니다")
	ChannelType channelType,

	@Schema(description = "수신자 목록", example = "[\"user1@email.com\", \"user2@email.com\"]")
	@NotEmpty(message = "receivers는 최소 1명 이상이어야 합니다")
	List<String> receivers,

	@Schema(description = "요청 멱등 키(중복 요청 방지)", example = "order-20260222-0001")
	String idempotencyKey
) {
}
