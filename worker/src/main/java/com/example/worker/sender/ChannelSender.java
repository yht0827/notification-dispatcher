package com.example.worker.sender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;

public interface ChannelSender {

	ChannelType getChannelType();

	SendResult send(Notification notification);

	default Map<Long, SendResult> sendBatch(List<Notification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return Map.of();
		}

		Map<Long, SendResult> results = new LinkedHashMap<>();
		for (Notification notification : notifications) {
			results.put(notification.getId(), send(notification));
		}
		return results;
	}
}
