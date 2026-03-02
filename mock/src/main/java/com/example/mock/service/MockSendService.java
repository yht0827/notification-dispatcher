package com.example.mock.service;

import com.example.mock.api.MockSendFailResponse;
import com.example.mock.api.MockSendRequest;
import com.example.mock.api.MockSendSuccessResponse;
import com.example.mock.config.MockMode;
import com.example.mock.config.MockProperties;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MockSendService {

    private static final Logger log = LoggerFactory.getLogger(MockSendService.class);

    private final MockProperties properties;

    public MockSendService(MockProperties properties) {
        this.properties = properties;
    }

    public MockSendSuccessResponse handleSend(MockSendRequest request) {
        long startedAtMillis = System.currentTimeMillis();
        MockMode mode = properties.getMode();

        boolean delayed = switch (mode) {
            case ALWAYS_DELAY -> true;
            case RANDOM -> shouldDelay();
            default -> false;
        };

        if (delayed) { applyDelay(); }

        boolean fail = switch (mode) {
            case ALWAYS_FAIL -> true;
            case RANDOM -> shouldFail();
            default -> false;
        };

        if (fail) { throw buildFailureException(request, startedAtMillis); }

        long latencyMs = System.currentTimeMillis() - startedAtMillis;
        String behavior = delayed ? "DELAY" : "SUCCESS";
        logRequest(request, behavior, HttpStatus.OK.value(), latencyMs);

        return new MockSendSuccessResponse(
                "SUCCESS",
                request.requestId(),
                request.channelType().name(),
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                latencyMs
        );
    }

    public MockSendFailResponse buildFailResponse(
            String requestId, String channelType, String errorCode,
            String message, long startedAtMillis) {
        long latencyMs = Math.max(0L, System.currentTimeMillis() - startedAtMillis);
        return new MockSendFailResponse(
                "FAIL", safeText(requestId), safeText(channelType),
                errorCode, message,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                latencyMs
        );
    }

    public void logFailure(String requestId, String channelType, String receiver,
                           int messageLength, int httpStatus, long latencyMs) {
        log.info(
                "mock_send requestId={} channelType={} receiver={} messageLength={} chosenBehavior=FAIL httpStatus={} latencyMs={}",
                safeText(requestId), safeText(channelType), safeText(receiver),
                messageLength, httpStatus, latencyMs
        );
    }

    private boolean shouldDelay() {
        return properties.getLatency().isEnabled()
                && randomHit(properties.getLatency().getProbability());
    }

    private boolean shouldFail() {
        return properties.getFailure().isEnabled()
                && randomHit(properties.getFailure().getProbability());
    }

    private boolean randomHit(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private void applyDelay() {
        int min = properties.getLatency().getMinMs();
        int max = properties.getLatency().getMaxMs();
        int delayMs = max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MockFailureException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "MOCK_INTERNAL",
                    "Interrupted while applying mock delay",
                    "UNKNOWN", "UNKNOWN", "UNKNOWN", 0, System.currentTimeMillis()
            );
        }
    }

    private MockFailureException buildFailureException(MockSendRequest request, long startedAtMillis) {
        List<Integer> types = properties.getFailure().getTypes();
        int selectedStatusCode = (types == null || types.isEmpty())
                ? 500 : types.get(ThreadLocalRandom.current().nextInt(types.size()));
        HttpStatus status = HttpStatus.resolve(selectedStatusCode);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        String errorCode = switch (status.value()) {
            case 503 -> "MOCK_UNAVAILABLE";
            case 429 -> "MOCK_RATE_LIMIT";
            default -> "MOCK_INTERNAL";
        };

        return new MockFailureException(
                status, errorCode, "Mock failure generated intentionally",
                request.requestId(), request.channelType().name(),
                request.receiver(), request.message().length(), startedAtMillis
        );
    }

    private void logRequest(MockSendRequest request, String behavior, int status, long latencyMs) {
        if (properties.getLog().isIncludeMaskedMessagePreview()) {
            log.info(
                    "mock_send requestId={} channelType={} receiver={} messageLength={} messagePreview={} chosenBehavior={} httpStatus={} latencyMs={}",
                    safeText(request.requestId()), safeText(request.channelType().name()),
                    safeText(request.receiver()), request.message().length(),
                    maskedPreview(request.message()), behavior, status, latencyMs
            );
            return;
        }
        log.info(
                "mock_send requestId={} channelType={} receiver={} messageLength={} chosenBehavior={} httpStatus={} latencyMs={}",
                safeText(request.requestId()), safeText(request.channelType().name()),
                safeText(request.receiver()), request.message().length(), behavior, status, latencyMs
        );
    }

    private String maskedPreview(String message) {
        int maxLength = properties.getLog().getMessagePreviewLength();
        return "*".repeat(Math.min(message.length(), maxLength));
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
