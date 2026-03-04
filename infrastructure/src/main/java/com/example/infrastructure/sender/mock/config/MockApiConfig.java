package com.example.infrastructure.sender.mock.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.infrastructure.sender.mock.caller.MockApiErrorDecoder;

@Configuration
@EnableConfigurationProperties(MockApiProperties.class)
@EnableFeignClients(basePackages = "com.example.infrastructure.sender.mock.caller")
public class MockApiConfig {

	@Bean
	public MockApiErrorDecoder mockApiErrorDecoder() {
		return new MockApiErrorDecoder();
	}
}
