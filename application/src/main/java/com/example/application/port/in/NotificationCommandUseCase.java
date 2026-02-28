package com.example.application.port.in;

import java.util.List;

import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;

public interface NotificationCommandUseCase {

	NotificationGroup request(SendCommand command);

	record SendCommand(
		String clientId,
		String sender,
		String title,
		String content,
		ChannelType channelType,
		List<String> receivers,
		String idempotencyKey
	) {
	}
}
