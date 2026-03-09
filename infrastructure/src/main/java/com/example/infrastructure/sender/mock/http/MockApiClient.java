package com.example.infrastructure.sender.mock.http;

import org.springframework.http.ResponseEntity;

import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;

public interface MockApiClient {

	ResponseEntity<MockApiSendSuccessResponse> sendEmail(MockApiSendRequest request);
	ResponseEntity<MockApiSendSuccessResponse> sendSms(MockApiSendRequest request);
	ResponseEntity<MockApiSendSuccessResponse> sendKakao(MockApiSendRequest request);
}
