package com.example.application.port.in;

import java.util.Optional;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationGroupReadResult;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.in.result.NotificationReadResult;

public interface NotificationWriteUseCase {

	NotificationCommandResult request(SendCommand command);

	Optional<NotificationReadResult> markAsRead(Long notificationId);

	Optional<NotificationGroupReadResult> markGroupAsRead(Long groupId);
}
