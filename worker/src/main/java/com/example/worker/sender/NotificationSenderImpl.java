package com.example.worker.sender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.SendResult;
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

	@Override
	public Map<Long, SendResult> sendBatch(List<Notification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return Map.of();
		}

		Map<ChannelType, List<Notification>> grouped = notifications.stream()
			.collect(
				LinkedHashMap::new,
				(map, notification) -> map.computeIfAbsent(notification.getGroup().getChannelType(), key -> new java.util.ArrayList<>())
					.add(notification),
				Map::putAll
			);

		Map<Long, SendResult> results = new LinkedHashMap<>();
		for (Map.Entry<ChannelType, List<Notification>> entry : grouped.entrySet()) {
			ChannelSender sender = senderFactory.getSender(entry.getKey());
			results.putAll(sender.sendBatch(entry.getValue()));
		}
		return results;
	}
}
