package com.example.api.exception;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.api.dto.response.ErrorResponse;
import com.example.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NotificationException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotificationException(NotificationException e) {
		log.warn("NotificationException: {}", e.getMessage());
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity
			.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.name(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		log.warn("ValidationException: {}", e.getMessage());
		List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
			.toList();

		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST.name(), "입력값이 올바르지 않습니다.", fieldErrors));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Unexpected error", e);
		return ResponseEntity
			.internalServerError()
			.body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage()));
	}
}
