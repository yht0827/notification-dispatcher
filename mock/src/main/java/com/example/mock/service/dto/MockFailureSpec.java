package com.example.mock.service.dto;

import org.springframework.http.HttpStatus;

public record MockFailureSpec(
	HttpStatus status,
	String errorCode
) {
}
