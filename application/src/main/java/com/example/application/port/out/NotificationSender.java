package com.example.application.port.out;

import com.example.domain.notification.Notification;

public interface NotificationSender {

	SendResult send(Notification notification);
}
