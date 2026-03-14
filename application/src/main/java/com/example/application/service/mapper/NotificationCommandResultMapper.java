package com.example.application.service.mapper;

import org.springframework.stereotype.Component;

import com.example.application.port.in.result.NotificationCommandResult;
import com.example.domain.notification.NotificationGroup;

@Component
public class NotificationCommandResultMapper {

	public NotificationCommandResult toResult(NotificationGroup group) {
		return new NotificationCommandResult(group.getId(), group.getTotalCount());
	}
}
