package com.example.api.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
@ConditionalOnProperty(name = "app.web.enabled", havingValue = "true", matchIfMissing = true)
public class AdminKeyFilterConfig {

	@Bean
	public FilterRegistrationBean<AdminKeyAuthFilter> adminKeyAuthFilter(ApiKeyProperties properties) {
		AdminKeyAuthFilter filter = new AdminKeyAuthFilter(properties.getAdminKey());
		FilterRegistrationBean<AdminKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
		registration.addUrlPatterns("/api/admin/*");
		registration.setOrder(0);
		return registration;
	}
}
