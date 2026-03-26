package com.example.worker.sender.mock.http;

import org.springframework.http.ResponseEntity;

import com.example.worker.sender.mock.dto.MockApiSendRequest;
import com.example.worker.sender.mock.dto.MockApiSendSuccessResponse;

public interface MockApiClient {

	ResponseEntity<MockApiSendSuccessResponse> send(MockApiSendRequest request);
}
