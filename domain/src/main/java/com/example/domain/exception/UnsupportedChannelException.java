package com.example.domain.exception;

import com.example.domain.notification.ChannelType;

public class UnsupportedChannelException extends DomainException {

	public UnsupportedChannelException(ChannelType channelType) {
		super("지원하지 않는 채널입니다: " + channelType);
	}
}
