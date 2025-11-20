package com.hms.servicename.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class ScaleKitWebhookController {

    @Value("${scalekit.webhook.secret}")
    private String webhookSecret;

    /**
     * Public webhook endpoint for ScaleKit directory events.
     * 
     * This endpoint:
     * - Is excluded from standard security chain (must be public)
     * - Reads X-Scalekit-Signature header
     * - Performs cryptographic signature validation
     * - Handles scalekit.dir.user.delete events for real-time de-provisioning
     */
    @PostMapping("/scalekit")
    public ResponseEntity<Void> handleScaleKitWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Scalekit-Signature") String signature) {
        
        // TODO: Implement webhook signature verification:
        // 1. Compute HMAC-SHA256 of rawBody using webhookSecret
        // 2. Compare with signature header (constant-time comparison)
        // 3. If invalid, return 401 Unauthorized
        
        // TODO: Parse the webhook event JSON
        // TODO: Handle scalekit.dir.user.delete event:
        //   - Extract user ID from event
        //   - De-provision user in local database (set active=false or delete)
        
        // Acknowledge immediately to prevent retries
        return ResponseEntity.ok().build();
    }
}

