package com.example.application.port.in;

import java.util.List;

import com.example.domain.notification.NotificationGroup;

public record NotificationGroupSlice(
	List<NotificationGroup> items,
	boolean hasNext,
	Long nextCursorId
) {
}
