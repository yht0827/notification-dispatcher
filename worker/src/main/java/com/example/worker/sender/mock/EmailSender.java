package com.example.worker.sender.mock;

import org.springframework.stereotype.Component;

import com.example.domain.notification.ChannelType;

@Component
public class EmailSender extends AbstractMockChannelSender {

	public EmailSender(MockApiSender mockApiSender) {
		super(mockApiSender, ChannelType.EMAIL);
	}
}
