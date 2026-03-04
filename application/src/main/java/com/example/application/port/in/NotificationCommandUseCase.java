package com.example.application.port.in;

import java.util.List;

import com.example.application.port.in.result.NotificationCommandResult;
import com.example.domain.notification.ChannelType;

public interface NotificationCommandUseCase {

	NotificationCommandResult request(SendCommand command);

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
