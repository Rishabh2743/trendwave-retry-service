package com.trendwave.retry_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI trendwaveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TrendWave Adaptive Retry Service API")
                        .description("Intelligent payment authorization retry logic for TrendWave marketplace")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TrendWave Payments Team")
                                .email("payments@trendwave.com")));
    }
}