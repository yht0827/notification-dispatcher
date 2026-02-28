package com.example.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에러 응답")
public record ErrorResponse(
	@Schema(description = "에러 코드", example = "INVALID_REQUEST")
	String code,

	@Schema(description = "에러 메시지", example = "잘못된 요청입니다.")
	String message,

	@Schema(description = "상세 에러 목록")
	List<FieldError> errors,

	@Schema(description = "발생 시각")
	LocalDateTime timestamp
) {
	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(code, message, List.of(), LocalDateTime.now());
	}

	public static ErrorResponse of(String code, String message, List<FieldError> errors) {
		return new ErrorResponse(code, message, errors, LocalDateTime.now());
	}

	@Schema(description = "필드 에러")
	public record FieldError(
		@Schema(description = "필드명", example = "title")
		String field,

		@Schema(description = "에러 메시지", example = "title은 필수입니다")
		String message
	) {
	}
}
