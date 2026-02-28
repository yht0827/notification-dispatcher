package com.example.infrastructure.sender;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.ChannelType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChannelSenderFactory {

	private final List<ChannelSender> senders;
	private Map<ChannelType, ChannelSender> senderMap;

	@PostConstruct
	void init() {
		senderMap = new EnumMap<>(ChannelType.class);
		for (ChannelSender sender : senders) {
			senderMap.put(sender.getChannelType(), sender);
		}
	}

	public ChannelSender getSender(ChannelType channelType) {
		ChannelSender sender = senderMap.get(channelType);
		if (sender == null) {
			throw new UnsupportedChannelException(channelType);
		}
		return sender;
	}
}
