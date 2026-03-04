package com.example.infrastructure.sender;

import org.springframework.stereotype.Component;

import com.example.domain.notification.ChannelType;
import com.example.infrastructure.sender.mock.MockApiSender;

@Component
public class KakaoSender extends AbstractMockChannelSender {

	public KakaoSender(MockApiSender mockApiSender) {
		super(mockApiSender, ChannelType.KAKAO);
	}
}
