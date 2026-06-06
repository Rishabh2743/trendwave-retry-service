package com.trendwave.retry_service.exception;

public class ProcessorNotFoundException extends RuntimeException {
    public ProcessorNotFoundException(String processorId) {
        super("Processor not found: " + processorId);
    }
}