package com.example.api.dto.response;

import java.util.List;

import com.example.application.port.in.NotificationGroupSlice;
import com.example.application.port.in.result.NotificationGroupResult;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "요청자별 알림 그룹 커서 기반 조회 응답")
public record NotificationGroupSliceResponse(
    @Schema(description = "알림 그룹 목록")
    List<NotificationGroupResponse> items,

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    boolean hasNext,

    @Schema(description = "다음 요청용 커서 ID", example = "123")
    Long nextCursorId
) {
	public static NotificationGroupSliceResponse from(NotificationGroupSlice<NotificationGroupResult> slice) {
		List<NotificationGroupResponse> items = slice.items().stream()
			.map(NotificationGroupResponse::from)
			.toList();
        return new NotificationGroupSliceResponse(items, slice.hasNext(), slice.nextCursorId());
    }
}
