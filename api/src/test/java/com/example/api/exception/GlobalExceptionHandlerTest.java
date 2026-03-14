package com.example.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.api.dto.response.ApiResponse;
import com.example.api.dto.response.ErrorResponse;

import jakarta.validation.ConstraintViolationException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	@DisplayName("NotificationException은 error ApiResponse로 변환한다")
	void handleNotificationException_returnsApiResponse() {
		NotificationException exception = new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND);

		ResponseEntity<ApiResponse<Void>> response = handler.handleNotificationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().error().code()).isEqualTo("NOTIFICATION_NOT_FOUND");
	}

	@Test
	@DisplayName("BindException은 field error 응답으로 변환한다")
	void handleBindException_returnsFieldErrors() {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(new FieldError(
			"request",
			"cursorId",
			"abc",
			false,
			new String[] {"typeMismatch"},
			null,
			"숫자여야 합니다"
		));
		BindException exception = new BindException(bindingResult);

		ResponseEntity<ErrorResponse> response = handler.handleBindException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().errors()).hasSize(1);
		assertThat(response.getBody().errors().getFirst().field()).isEqualTo("cursorId");
		assertThat(response.getBody().errors().getFirst().message()).contains("형식이 올바르지 않습니다");
	}

	@Test
	@DisplayName("타입 불일치 예외는 필드별 메시지로 변환한다")
	void handleMethodArgumentTypeMismatchException_returnsFieldMessage() {
		MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
			"abc",
			Long.class,
			"cursorId",
			(MethodParameter) null,
			new IllegalArgumentException("bad type")
		);

		ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentTypeMismatchException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().errors()).hasSize(1);
		assertThat(response.getBody().errors().getFirst().field()).isEqualTo("cursorId");
		assertThat(response.getBody().errors().getFirst().message()).contains("Long 타입");
	}

	@Test
	@DisplayName("requiredType이 없으면 unknown 타입 메시지를 사용한다")
	void handleMethodArgumentTypeMismatchException_usesUnknownWhenRequiredTypeMissing() {
		MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
			"abc",
			null,
			"cursorId",
			(MethodParameter) null,
			new IllegalArgumentException("bad type")
		);

		ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentTypeMismatchException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().errors().getFirst().message()).contains("unknown 타입");
	}

	@Test
	@DisplayName("읽을 수 없는 JSON 예외는 INVALID_REQUEST로 변환한다")
	void handleHttpMessageNotReadableException_returnsInvalidRequest() {
		HttpMessageNotReadableException exception = new HttpMessageNotReadableException("bad json");

		ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
	}

	@Test
	@DisplayName("핸들러 메서드 검증 예외는 INVALID_REQUEST로 변환한다")
	void handleHandlerMethodValidationException_returnsInvalidRequest() {
		HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
		when(exception.getMessage()).thenReturn("validation failed");

		ResponseEntity<ErrorResponse> response = handler.handleHandlerMethodValidationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
	}

	@Test
	@DisplayName("ConstraintViolationException은 INVALID_REQUEST로 변환한다")
	void handleConstraintViolationException_returnsInvalidRequest() {
		ConstraintViolationException exception = new ConstraintViolationException("constraint", Set.of());

		ResponseEntity<ErrorResponse> response = handler.handleConstraintViolationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
	}

	@Test
	@DisplayName("예상치 못한 예외는 INTERNAL_ERROR ApiResponse로 변환한다")
	void handleException_returnsInternalError() {
		ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException("boom"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
	}
}
