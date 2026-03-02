package com.example.infrastructure.sender.mock.caller;

import com.example.infrastructure.sender.mock.dto.MockApiSendFailResponse;
import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MockApiErrorDecoder implements ErrorDecoder {

	private final ObjectMapper objectMapper;

	@Override
	public Exception decode(String methodKey, Response response) {
		int status = response.status();
		String message = resolveMessage(response);

		if (status == 429 || status >= 500) {
			return new MockApiRetryableException("외부 API 재시도 가능 오류(" + status + "): " + message);
		}

		return new MockApiNonRetryableException("외부 API 재시도 불가 오류(" + status + "): " + message);
	}

	private String resolveMessage(Response response) {
		if (response.body() == null) {
			return "no response body";
		}

		try {
			byte[] body = response.body().asInputStream().readAllBytes();
			if (body.length == 0) {
				return "no response body";
			}

			MockApiSendFailResponse failResponse = objectMapper.readValue(body, MockApiSendFailResponse.class);
			if (failResponse.message() != null && !failResponse.message().isBlank()) {
				return failResponse.message();
			}
		} catch (Exception e) {
			log.debug("mock API 실패 응답 파싱 실패: status={}, reason={}", response.status(), e.getMessage());
		}

		return "unknown error";
	}
}
