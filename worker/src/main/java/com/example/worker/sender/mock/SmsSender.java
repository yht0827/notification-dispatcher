package com.example.worker.sender.mock;

import org.springframework.stereotype.Component;

import com.example.domain.notification.ChannelType;

@Component
public class SmsSender extends AbstractMockChannelSender {

	public SmsSender(MockApiSender mockApiSender) {
		super(mockApiSender, ChannelType.SMS);
	}
}
