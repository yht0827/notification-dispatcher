package com.example.application.port.in;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;

public interface NotificationWriteUseCase {

	NotificationCommandResult request(SendCommand command);

	boolean markAsRead(Long notificationId);
}
