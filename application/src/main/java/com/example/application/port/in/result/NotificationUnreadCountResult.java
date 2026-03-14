package com.example.application.port.in.result;

public record NotificationUnreadCountResult(
	String receiver,
	long unreadCount
) {
}
