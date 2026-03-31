package com.example.worker.sender.mock;

import org.springframework.stereotype.Component;

import com.example.domain.notification.ChannelType;
import com.example.worker.sender.mock.config.MockApiProperties;
import com.example.worker.sender.mock.http.EmailMockApiCaller;

@Component
public class EmailSender extends AbstractMockChannelSender {

	public EmailSender(EmailMockApiCaller caller, MockApiProperties properties) {
		super(caller, properties, ChannelType.EMAIL);
	}
}
