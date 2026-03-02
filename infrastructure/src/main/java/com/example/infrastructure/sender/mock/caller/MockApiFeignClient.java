package com.example.infrastructure.sender.mock.caller;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.dto.MockApiSendSuccessResponse;

@FeignClient(name = "mockApi", url = "${notification.external.mock.base-url}")
public interface MockApiFeignClient {

	@PostMapping("${notification.external.mock.send-path}")
	MockApiSendSuccessResponse send(@RequestBody MockApiSendRequest request);
}
