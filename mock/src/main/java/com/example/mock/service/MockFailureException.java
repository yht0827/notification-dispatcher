package com.example.mock.service;

import org.springframework.http.HttpStatus;

public class MockFailureException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String requestId;
    private final String channelType;
    private final String receiver;
    private final int messageLength;
    private final long startedAtMillis;

    public MockFailureException(
            HttpStatus status,
            String errorCode,
            String message,
            String requestId,
            String channelType,
            String receiver,
            int messageLength,
            long startedAtMillis
    ) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.requestId = requestId;
        this.channelType = channelType;
        this.receiver = receiver;
        this.messageLength = messageLength;
        this.startedAtMillis = startedAtMillis;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getRequestId() { return requestId; }
    public String getChannelType() { return channelType; }
    public String getReceiver() { return receiver; }
    public int getMessageLength() { return messageLength; }
    public long getStartedAtMillis() { return startedAtMillis; }
}
