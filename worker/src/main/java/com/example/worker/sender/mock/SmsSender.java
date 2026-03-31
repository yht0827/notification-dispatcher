package com.example.worker.sender.mock;

import org.springframework.stereotype.Component;

import com.example.domain.notification.ChannelType;
import com.example.worker.sender.mock.config.MockApiProperties;
import com.example.worker.sender.mock.http.SmsMockApiCaller;

@Component
public class SmsSender extends AbstractMockChannelSender {

	public SmsSender(SmsMockApiCaller caller, MockApiProperties properties) {
		super(caller, properties, ChannelType.SMS);
	}
}
