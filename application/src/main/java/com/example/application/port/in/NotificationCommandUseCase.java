package com.example.application.port.in;

import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;

public interface NotificationCommandUseCase {

	NotificationCommandResult request(SendCommand command);
}
