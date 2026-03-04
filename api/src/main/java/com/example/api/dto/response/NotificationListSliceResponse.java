package com.example.api.dto.response;

import java.util.List;

import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationListResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 묶음 커서 기반 조회 응답")
public record NotificationListSliceResponse(
	@Schema(description = "알림 묶음 목록")
	List<NotificationListResponse> items,

	@Schema(description = "다음 페이지 존재 여부", example = "true")
	boolean hasNext,

	@Schema(description = "다음 요청용 커서 ID", example = "123")
	Long nextCursorId
) {
	public static NotificationListSliceResponse from(CursorSlice<NotificationListResult> slice) {
		List<NotificationListResponse> items = slice.items().stream()
			.map(NotificationListResponse::from)
			.toList();

		return new NotificationListSliceResponse(items, slice.hasNext(), slice.nextCursorId());
	}
}
