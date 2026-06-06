package com.trendwave.retry_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "retry")
public class AppConfig {

    private int maxAttempts = 3;
    private int defaultDelaySeconds = 10;
    private Map<String, ProcessorConfig> processors = new HashMap<>();

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public int getDefaultDelaySeconds() { return defaultDelaySeconds; }
    public void setDefaultDelaySeconds(int defaultDelaySeconds) { this.defaultDelaySeconds = defaultDelaySeconds; }

    public Map<String, ProcessorConfig> getProcessors() { return processors; }
    public void setProcessors(Map<String, ProcessorConfig> processors) { this.processors = processors; }

    public static class ProcessorConfig {
        private int timeoutRetryDelaySeconds = 10;
        private int maxRetryAttempts = 3;
        private double timeoutRecoveryRate = 0.40;

        public int getTimeoutRetryDelaySeconds() { return timeoutRetryDelaySeconds; }
        public void setTimeoutRetryDelaySeconds(int v) { this.timeoutRetryDelaySeconds = v; }

        public int getMaxRetryAttempts() { return maxRetryAttempts; }
        public void setMaxRetryAttempts(int v) { this.maxRetryAttempts = v; }

        public double getTimeoutRecoveryRate() { return timeoutRecoveryRate; }
        public void setTimeoutRecoveryRate(double v) { this.timeoutRecoveryRate = v; }
    }
}