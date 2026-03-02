package com.example.infrastructure.sender.mock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.external.mock")
public class MockApiProperties {

	private boolean enabled = true;
}
