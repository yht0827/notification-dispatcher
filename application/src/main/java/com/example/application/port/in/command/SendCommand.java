package com.example.application.port.in.command;

import java.util.List;

import com.example.domain.notification.ChannelType;

public record SendCommand(
	String clientId,
	String sender,
	String title,
	String content,
	ChannelType channelType,
	List<String> receivers,
	String idempotencyKey
) {
}
