package com.example.worker.sender.mock;

import org.springframework.stereotype.Component;

import com.example.domain.notification.ChannelType;
import com.example.worker.sender.mock.config.MockApiProperties;
import com.example.worker.sender.mock.http.KakaoMockApiCaller;

@Component
public class KakaoSender extends AbstractMockChannelSender {

	public KakaoSender(KakaoMockApiCaller caller, MockApiProperties properties) {
		super(caller, properties, ChannelType.KAKAO);
	}
}
