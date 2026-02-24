package com.example.application.port.in;

import java.util.List;

import com.example.domain.notification.NotificationGroup;

public record NotificationGroupSlice(
	List<NotificationGroup> items,
	boolean hasNext,
	Long nextCursorId
) {
	public static NotificationGroupSlice of(List<NotificationGroup> fetched, int requestedSize) {
		boolean hasNext = fetched.size() > requestedSize;
		List<NotificationGroup> items = List.copyOf(
			hasNext ? fetched.subList(0, requestedSize) : fetched
		);
		return new NotificationGroupSlice(
			items,
			hasNext,
			hasNext ? items.getLast().getId() : null
		);
	}
}
