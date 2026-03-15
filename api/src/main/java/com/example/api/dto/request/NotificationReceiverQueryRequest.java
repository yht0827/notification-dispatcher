package com.example.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "수신자별 메시지 내역 조회 요청")
public record NotificationReceiverQueryRequest(
	@Schema(description = "수신자", example = "user@email.com")
	@NotBlank(message = "receiver는 비어 있을 수 없습니다.")
	String receiver,

	@Schema(description = "커서 ID", example = "120")
	@Positive(message = "cursorId는 1 이상이어야 합니다")
	Long cursorId,

	@Schema(description = "조회 개수(기본 20, 최대 100)", example = "20")
	@Min(value = 1, message = "size는 1 이상이어야 합니다")
	@Max(value = 100, message = "size는 100 이하여야 합니다")
	Integer size
) {
	private static final int DEFAULT_SIZE = 20;

	public int resolveSize() {
		return size == null ? DEFAULT_SIZE : size;
	}
}
