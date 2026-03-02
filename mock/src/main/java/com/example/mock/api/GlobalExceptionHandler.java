package com.example.mock.api;

import com.example.mock.service.MockFailureException;
import com.example.mock.service.MockSendService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MockSendService mockSendService;

    public GlobalExceptionHandler(MockSendService mockSendService) {
        this.mockSendService = mockSendService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MockSendFailResponse> handleValidation(MethodArgumentNotValidException ex) {
        long startedAtMillis = System.currentTimeMillis();
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request");

        MockSendFailResponse body = mockSendService.buildFailResponse(
                "UNKNOWN", "UNKNOWN", "MOCK_BAD_REQUEST", errorMessage, startedAtMillis
        );
        mockSendService.logFailure(body.requestId(), body.channelType(), "UNKNOWN", 0, 400, body.latencyMs());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MockSendFailResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        long startedAtMillis = System.currentTimeMillis();
        MockSendFailResponse body = mockSendService.buildFailResponse(
                "UNKNOWN", "UNKNOWN", "MOCK_BAD_REQUEST", "Malformed JSON request", startedAtMillis
        );
        mockSendService.logFailure(body.requestId(), body.channelType(), "UNKNOWN", 0, 400, body.latencyMs());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MockFailureException.class)
    public ResponseEntity<MockSendFailResponse> handleMockFailure(MockFailureException ex) {
        MockSendFailResponse body = mockSendService.buildFailResponse(
                ex.getRequestId(), ex.getChannelType(), ex.getErrorCode(),
                ex.getMessage(), ex.getStartedAtMillis()
        );
        mockSendService.logFailure(
                body.requestId(), body.channelType(), ex.getReceiver(),
                ex.getMessageLength(), ex.getStatus().value(), body.latencyMs()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MockSendFailResponse> handleUnexpected(Exception ex) {
        long startedAtMillis = System.currentTimeMillis();
        MockSendFailResponse body = new MockSendFailResponse(
                "FAIL", "UNKNOWN", "UNKNOWN", "MOCK_INTERNAL", "Unexpected internal error",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                Math.max(0L, System.currentTimeMillis() - startedAtMillis)
        );
        mockSendService.logFailure(body.requestId(), body.channelType(), "UNKNOWN", 0, 500, body.latencyMs());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
