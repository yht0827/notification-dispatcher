package com.example.mock.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record MockSendRequest(
        @NotBlank(message = "requestId is required")
        String requestId,
        @NotNull(message = "channelType is required")
        ChannelType channelType,
        @NotBlank(message = "receiver is required")
        String receiver,
        @NotBlank(message = "message is required")
        String message,
        Map<String, Object> metadata
) {
}
