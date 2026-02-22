package com.example.infrastructure.sender;

import org.springframework.stereotype.Component;

import com.example.application.port.out.NotificationSender.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EmailSender implements ChannelSender {

	@Override
	public ChannelType getChannelType() {
		return ChannelType.EMAIL;
	}

	@Override
	public SendResult send(Notification notification) {
		try {
			// TODO: 실제 이메일 발송 로직 구현
			log.info("[EMAIL] 발송: to={}, title={}",
				notification.getReceiver(),
				notification.getGroup().getTitle());

			return SendResult.success();
		} catch (Exception e) {
			log.error("[EMAIL] 발송 실패: to={}, error={}",
				notification.getReceiver(), e.getMessage());
			return SendResult.fail(e.getMessage());
		}
	}
}
