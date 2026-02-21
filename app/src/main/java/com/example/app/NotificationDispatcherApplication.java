package com.example.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example")
public class NotificationDispatcherApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationDispatcherApplication.class, args);
	}

}
