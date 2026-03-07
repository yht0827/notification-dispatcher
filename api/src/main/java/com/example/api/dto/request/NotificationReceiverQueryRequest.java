package com.example.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "수신자별 알림 목록 조회 요청")
public record NotificationReceiverQueryRequest(
	@Schema(description = "수신자", example = "user@email.com")
	@NotBlank(message = "receiver는 필수입니다")
	String receiver
) {
}
