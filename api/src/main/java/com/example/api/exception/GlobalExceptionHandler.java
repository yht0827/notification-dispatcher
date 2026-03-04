package com.example.api.exception;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.api.dto.response.ErrorResponse;
import com.example.api.response.ApiResponse;

import jakarta.validation.ConstraintViolationException;
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
		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST.name(), "입력값이 올바르지 않습니다.",
				extractFieldErrors(e.getBindingResult())));
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
		log.warn("BindException: {}", e.getMessage());
		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST.name(), "입력값이 올바르지 않습니다.",
				extractFieldErrors(e.getBindingResult())));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException e) {
		log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
		String field = e.getName();
		String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
		String message = String.format("'%s' 필드는 %s 타입이어야 합니다.", field, requiredType);

		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST.name(), "입력 데이터 타입이 올바르지 않습니다.",
				List.of(new ErrorResponse.FieldError(field, message))));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
		log.warn("HttpMessageNotReadableException: {}", e.getMessage());
		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST.name(), "요청 본문을 읽을 수 없거나 형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
		log.warn("HandlerMethodValidationException: {}", e.getMessage());
		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST.name(), "입력값이 올바르지 않습니다."));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
		log.warn("ConstraintViolationException: {}", e.getMessage());
		return ResponseEntity
			.badRequest()
			.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST.name(), "입력값이 올바르지 않습니다."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Unexpected error", e);
		return ResponseEntity
			.internalServerError()
			.body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage()));
	}

	private List<ErrorResponse.FieldError> extractFieldErrors(BindingResult bindingResult) {
		return bindingResult.getFieldErrors()
			.stream()
			.map(error -> {
				String field = error.getField();
				String message = error.getDefaultMessage();
				if ("typeMismatch".equals(error.getCode())) {
					message = String.format("'%s' 필드의 형식이 올바르지 않습니다.", field);
				}
				return new ErrorResponse.FieldError(field, message);
			})
			.toList();
	}
}
