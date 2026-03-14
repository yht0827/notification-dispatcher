package com.example.api.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "api")
public class ApiKeyProperties {

	private List<String> validKeys = new ArrayList<>();
	private String adminKey;
}
