package com.example.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("Notification Dispatcher API")
				.description("알림 발송 시스템 API")
				.version("v1.0.0"))
			.addSecurityItem(new SecurityRequirement().addList("ApiKey"))
			.components(new Components()
				.addSecuritySchemes("ApiKey", new SecurityScheme()
					.name("X-Api-Key")
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)));
	}
}
