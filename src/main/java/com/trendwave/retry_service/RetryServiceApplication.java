package com.trendwave.retry_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RetryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RetryServiceApplication.class, args);
    }
}
