package com.hms.servicename.workflow;

import com.hms.bff.client.workflow.api.OnboardingApi;
import com.hms.bff.client.workflow.model.OnboardingResponse;
import com.hms.bff.client.workflow.model.StartOnboardingRequest;
import org.springframework.stereotype.Service;

@Service
public class WorkflowServiceClient {

    private final OnboardingApi onboardingApi;

    public WorkflowServiceClient(OnboardingApi onboardingApi) {
        this.onboardingApi = onboardingApi;
    }

    public OnboardingResponse startOnboarding(String tenantName, String adminEmail) {
        StartOnboardingRequest request = new StartOnboardingRequest();
        request.setTenantName(tenantName);
        request.setAdminEmail(adminEmail);
        return onboardingApi.startOnboarding(request).getBody();
    }
}
