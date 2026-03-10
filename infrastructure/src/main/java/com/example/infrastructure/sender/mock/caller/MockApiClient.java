package com.example.infrastructure.sender.mock.caller;

import org.springframework.http.ResponseEntity;

import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;

public interface MockApiClient {

	ResponseEntity<MockApiSendSuccessResponse> send(MockApiSendRequest request);
}
