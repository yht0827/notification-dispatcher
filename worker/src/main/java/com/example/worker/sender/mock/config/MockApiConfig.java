package com.example.worker.sender.mock.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.worker.sender.mock.http.MockApiErrorDecoder;

@Configuration
@EnableConfigurationProperties(MockApiProperties.class)
@EnableFeignClients(basePackages = "com.example.worker.sender.mock.http")
public class MockApiConfig {

	@Bean
	public MockApiErrorDecoder mockApiErrorDecoder() {
		return new MockApiErrorDecoder();
	}
}
