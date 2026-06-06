package com.trendwave.retry_service.service;

import com.trendwave.retry_service.domain.model.ProcessorProfile;

import java.util.List;

public interface ProcessorProfileService {
    ProcessorProfile getProfile(String processorId);
    void updateProfile(String processorId, ProcessorProfile profile);
    List<ProcessorProfile> getAllProfiles();
}