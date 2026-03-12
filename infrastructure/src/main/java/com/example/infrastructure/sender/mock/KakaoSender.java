package com.example.infrastructure.sender.mock;

import org.springframework.stereotype.Component;

import com.example.domain.notification.ChannelType;

@Component
public class KakaoSender extends AbstractMockChannelSender {

	public KakaoSender(MockApiSender mockApiSender) {
		super(mockApiSender, ChannelType.KAKAO);
	}
}
