package com.example.worker.sender.mock.http;

import com.example.application.port.out.SendResult;
import com.example.worker.sender.mock.dto.MockApiSendRequest;

public interface ChannelMockApiCaller {
	SendResult call(MockApiSendRequest request);
}
