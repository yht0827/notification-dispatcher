package com.example.infrastructure.sender.mock.caller;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;

@FeignClient(
	name = "${notification.external.mock.name:mockApi}",
	url = "${notification.external.mock.base-url}"
)
public interface MockApiFeignClient extends MockApiClient {

	@Override
	@PostMapping("/mock/send")
	ResponseEntity<MockApiSendSuccessResponse> send(@RequestBody MockApiSendRequest request);
}
