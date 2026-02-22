package com.example.api.exception;

import lombok.Getter;

@Getter
public class NotificationException extends RuntimeException {

	private final ErrorCode errorCode;

	public NotificationException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public NotificationException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
