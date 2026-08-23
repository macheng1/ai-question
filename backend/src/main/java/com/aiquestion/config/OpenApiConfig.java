package com.aiquestion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI kaoshiPlatformOpenAPI() {
        return new OpenAPI().info(new Info().title("登科企考 API").version("v1.0.0").description("企业公开考试平台接口文档"));
    }
}
