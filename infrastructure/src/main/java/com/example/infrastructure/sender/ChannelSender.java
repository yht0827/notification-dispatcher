package com.example.infrastructure.sender;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;

public interface ChannelSender {

	ChannelType getChannelType();

	SendResult send(Notification notification);
}
