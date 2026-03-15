package com.example.api.dto.response;

import java.util.List;

import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "수신자별 메시지 내역 조회 응답")
public record NotificationSliceResponse(
	@Schema(description = "알림 목록")
	List<NotificationResponse> items,

	@Schema(description = "다음 페이지 존재 여부", example = "true")
	boolean hasNext,

	@Schema(description = "다음 요청용 커서 ID", example = "123")
	Long nextCursorId
) {
	public static NotificationSliceResponse from(CursorSlice<NotificationResult> slice) {
		List<NotificationResponse> items = slice.items().stream()
			.map(NotificationResponse::from)
			.toList();
		return new NotificationSliceResponse(items, slice.hasNext(), slice.nextCursorId());
	}
}
