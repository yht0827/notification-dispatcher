package com.example.infrastructure.sender;

import org.springframework.stereotype.Component;

import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.result.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationSenderImpl implements NotificationSender {

	private final ChannelSenderFactory senderFactory;

	@Override
	public SendResult send(Notification notification) {
		ChannelType channelType = notification.getGroup().getChannelType();
		ChannelSender sender = senderFactory.getSender(channelType);
		return sender.send(notification);
	}
}
