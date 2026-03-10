package com.example.mock.api;

import com.example.mock.service.MockFailureException;
import com.example.mock.service.MockSendService;
import com.example.mock.service.dto.MockFailResult;
import com.example.mock.service.dto.MockFailureLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String ERROR_CODE_BAD_REQUEST = "MOCK_BAD_REQUEST";
    private static final String ERROR_CODE_INTERNAL = "MOCK_INTERNAL";

    private final MockSendService mockSendService;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MockSendFailResponse> handleValidation(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request");

        return failWithUnknownContext(
                HttpStatus.BAD_REQUEST,
                ERROR_CODE_BAD_REQUEST,
                errorMessage,
                nowMillis()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MockSendFailResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return failWithUnknownContext(
                HttpStatus.BAD_REQUEST,
                ERROR_CODE_BAD_REQUEST,
                "Malformed JSON request",
                nowMillis()
        );
    }

    @ExceptionHandler(MockFailureException.class)
    public ResponseEntity<MockSendFailResponse> handleMockFailure(MockFailureException ex) {
        MockSendFailResponse body = mockSendService.buildFailResponse(
                new MockFailResult(
                        ex.getRequestId(),
                        ex.getChannelType(),
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getStartedAtMillis()
                )
        );

        mockSendService.logFailure(
                new MockFailureLog(
                        body.requestId(),
                        ex.getChannelType(),
                        ex.getReceiver(),
                        ex.getMessageLength(),
                        ex.getStatus().value(),
                        body.latencyMs()
                )
        );

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(ex.getStatus());
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS && ex.getRetryAfterSeconds() != null) {
            responseBuilder.header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        }
        return responseBuilder.body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MockSendFailResponse> handleUnexpected(Exception ex) {
        return failWithUnknownContext(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ERROR_CODE_INTERNAL,
                "Unexpected internal error",
                nowMillis()
        );
    }

    private ResponseEntity<MockSendFailResponse> failWithUnknownContext(
            HttpStatus status,
            String errorCode,
            String message,
            long startedAtMillis
    ) {
        MockSendFailResponse body = mockSendService.buildFailResponse(
                MockFailResult.unknown(errorCode, message, startedAtMillis)
        );

        mockSendService.logFailure(
                MockFailureLog.unknown(status.value(), body.latencyMs())
        );

        return ResponseEntity.status(status).body(body);
    }

    private long nowMillis() {
        return System.currentTimeMillis();
    }
}
