package com.example.api.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// Common
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// Notification
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
	NOTIFICATION_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 그룹을 찾을 수 없습니다."),
	INVALID_CHANNEL_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 채널 타입입니다."),
	EMPTY_RECEIVERS(HttpStatus.BAD_REQUEST, "수신자가 비어있습니다.");

	private final HttpStatus status;
	private final String message;
}
