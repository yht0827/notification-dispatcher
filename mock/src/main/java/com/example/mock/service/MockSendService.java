package com.example.mock.service;

import com.example.mock.api.MockSendFailResponse;
import com.example.mock.api.MockSendRequest;
import com.example.mock.api.MockSendSuccessResponse;
import com.example.mock.config.MockMode;
import com.example.mock.config.MockProperties;
import com.example.mock.service.dto.MockFailResult;
import com.example.mock.service.dto.MockFailureLog;
import com.example.mock.service.dto.MockSendContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockSendService {

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String UNKNOWN = "UNKNOWN";

    private final MockProperties properties;
    private final MockBehaviorDecider behaviorDecider;

    public MockSendSuccessResponse handleSend(MockSendRequest request) {
        MockSendContext context = MockSendContext.from(request, nowMillis());
        MockMode mode = properties.getMode();

        boolean delayed = behaviorDecider.shouldDelay(mode);
        if (delayed) {
            behaviorDecider.applyDelay(context);
        }

        if (behaviorDecider.shouldFail(mode)) {
            throw behaviorDecider.buildFailureException(context);
        }

        long latencyMs = elapsedSince(context.startedAtMillis());
        logSuccess(context, delayed ? "DELAY" : RESULT_SUCCESS, latencyMs);

        return new MockSendSuccessResponse(
                RESULT_SUCCESS,
                context.requestId(),
                context.channelType(),
                nowUtc(),
                latencyMs
        );
    }

    public MockSendFailResponse buildFailResponse(MockFailResult failResult) {
        long latencyMs = elapsedSince(failResult.startedAtMillis());
        return new MockSendFailResponse(
                RESULT_FAIL,
                safeText(failResult.requestId()),
                safeText(failResult.channelType()),
                failResult.errorCode(),
                failResult.message(),
                nowUtc(),
                latencyMs
        );
    }

    public void logFailure(MockFailureLog failureLog) {
        log.info(
                "mock_send requestId={} channelType={} receiver={} messageLength={} chosenBehavior=FAIL httpStatus={} latencyMs={}",
                safeText(failureLog.requestId()),
                safeText(failureLog.channelType()),
                safeText(failureLog.receiver()),
                failureLog.messageLength(),
                failureLog.httpStatus(),
                failureLog.latencyMs()
        );
    }

    private void logSuccess(MockSendContext context, String behavior, long latencyMs) {
        if (properties.getLog().isIncludeMaskedMessagePreview()) {
            log.info(
                    "mock_send requestId={} channelType={} receiver={} messageLength={} messagePreview={} chosenBehavior={} httpStatus={} latencyMs={}",
                    context.requestId(),
                    context.channelType(),
                    context.receiver(),
                    context.messageLength(),
                    maskedPreview(context.messageOrEmpty()),
                    behavior,
                    HttpStatus.OK.value(),
                    latencyMs
            );
            return;
        }

        log.info(
                "mock_send requestId={} channelType={} receiver={} messageLength={} chosenBehavior={} httpStatus={} latencyMs={}",
                context.requestId(),
                context.channelType(),
                context.receiver(),
                context.messageLength(),
                behavior,
                HttpStatus.OK.value(),
                latencyMs
        );
    }

    private String maskedPreview(String message) {
        if (message.isBlank()) {
            return "";
        }
        int maxLength = properties.getLog().getMessagePreviewLength();
        return "*".repeat(Math.min(message.length(), maxLength));
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    private long elapsedSince(long startedAtMillis) {
        return Math.max(0L, nowMillis() - startedAtMillis);
    }

    private long nowMillis() {
        return System.currentTimeMillis();
    }
}
