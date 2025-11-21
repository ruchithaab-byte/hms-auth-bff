package com.hms.servicename.controller;

import com.hms.bff.client.workflow.model.OnboardingResponse;
import com.hms.bff.client.workflow.model.StartOnboardingRequest;
import com.hms.servicename.workflow.WorkflowServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/onboarding")
public class OnboardingController {

    private final WorkflowServiceClient workflowServiceClient;

    public OnboardingController(WorkflowServiceClient workflowServiceClient) {
        this.workflowServiceClient = workflowServiceClient;
    }

    @PostMapping("/start")
    public ResponseEntity<OnboardingResponse> startOnboarding(@RequestBody StartOnboardingRequest request) {
        OnboardingResponse response = workflowServiceClient.startOnboarding(
            request.getTenantName(),
            request.getAdminEmail()
        );
        return ResponseEntity.accepted().body(response);
    }
}

