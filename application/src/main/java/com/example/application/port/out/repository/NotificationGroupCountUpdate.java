package com.example.application.port.out.repository;

public record NotificationGroupCountUpdate(
	Long groupId,
	int sentDelta,
	int failedDelta
) {
}
