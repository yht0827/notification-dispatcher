package com.example.application.port.in.result;

public record NotificationCommandResult(
	Long groupId,
	int totalCount
) {
}
