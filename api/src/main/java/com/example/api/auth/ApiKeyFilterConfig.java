package com.example.api.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeyFilterConfig {

	@Bean
	public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(ApiKeyProperties properties) {
		ApiKeyAuthFilter filter = new ApiKeyAuthFilter(new java.util.HashSet<>(properties.getValidKeys()));
		FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
		registration.addUrlPatterns("/api/*");
		registration.setOrder(1);
		return registration;
	}
}
