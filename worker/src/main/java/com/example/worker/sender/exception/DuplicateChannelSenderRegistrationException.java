package com.example.worker.sender.exception;

import com.example.domain.notification.ChannelType;

public class DuplicateChannelSenderRegistrationException extends RuntimeException {

	public DuplicateChannelSenderRegistrationException(ChannelType channelType) {
		super("중복 ChannelSender 등록: " + channelType);
	}
}
