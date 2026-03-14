package com.example.infrastructure.sender;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.ChannelType;
import com.example.infrastructure.sender.exception.DuplicateChannelSenderRegistrationException;

@Component
public class ChannelSenderFactory {

	private final Map<ChannelType, ChannelSender> senderMap;

	public ChannelSenderFactory(List<ChannelSender> senders) {
		Map<ChannelType, ChannelSender> map = new EnumMap<>(ChannelType.class);
		for (ChannelSender sender : senders) {
			ChannelSender existing = map.put(sender.getChannelType(), sender);
			if (existing != null) {
				throw new DuplicateChannelSenderRegistrationException(sender.getChannelType());
			}
		}
		this.senderMap = Collections.unmodifiableMap(map);
	}

	public ChannelSender getSender(ChannelType channelType) {
		ChannelSender sender = senderMap.get(channelType);
		if (sender == null) {
			throw new UnsupportedChannelException(channelType);
		}
		return sender;
	}
}
