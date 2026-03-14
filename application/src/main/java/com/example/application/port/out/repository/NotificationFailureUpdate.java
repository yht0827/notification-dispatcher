package com.example.application.port.out.repository;

public record NotificationFailureUpdate(
	Long notificationId,
	String failReason
) {
}
