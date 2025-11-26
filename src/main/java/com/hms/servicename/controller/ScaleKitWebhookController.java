package com.hms.servicename.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.lib.common.security.PermitSyncService;
import com.hms.servicename.model.UserEntity;
import com.hms.servicename.model.UserRoleEntity;
import com.hms.servicename.repository.UserRepository;
import com.hms.servicename.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Webhook endpoint for ScaleKit directory events.
 * 
 * This endpoint serves as a fail-safe: if an admin changes a user in ScaleKit
 * dashboard
 * (bypassing HMS UI), this webhook catches it and updates the local shadow DB.
 * 
 * CRITICAL: Must verify webhook signature to prevent unauthorized updates.
 */
@RestController
@RequestMapping("/webhooks") // Changed from "/api/webhooks" to match ScaleKit webhook URL
public class ScaleKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ScaleKitWebhookController.class);

    @Value("${scalekit.webhook.secret}")
    private String webhookSecret;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermitSyncService permitSyncService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ScaleKitWebhookController(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PermitSyncService permitSyncService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.permitSyncService = permitSyncService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle ScaleKit webhook events.
     * 
     * Events handled:
     * - scalekit.dir.user.create
     * - scalekit.dir.user.update
     * - scalekit.dir.user.delete
     * - scalekit.dir.organization.create
     * - scalekit.dir.organization.update
     * - scalekit.dir.organization.delete
     */
    @PostMapping("/scalekit")
    @Transactional
    public ResponseEntity<Void> handleScaleKitWebhook(
            @RequestBody String rawBody,
            @RequestHeader("X-Scalekit-Signature") String signature) {

        try {
            // 1. Verify webhook signature (CRITICAL for security)
            if (!verifySignature(rawBody, signature, webhookSecret)) {
                log.warn("Invalid webhook signature - rejecting request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // 2. Parse webhook event JSON
            JsonNode event = objectMapper.readTree(rawBody);
            String eventType = event.get("type").asText();
            log.info("Received ScaleKit webhook event: {}", eventType);

            // 3. Handle different event types
            switch (eventType) {
                case "scalekit.dir.user.create":
                case "scalekit.dir.user.update":
                    handleUserCreateOrUpdate(event);
                    break;
                case "scalekit.dir.user.delete":
                    handleUserDelete(event);
                    break;
                case "scalekit.dir.organization.create":
                case "scalekit.dir.organization.update":
                    handleOrganizationCreateOrUpdate(event);
                    break;
                case "scalekit.dir.organization.delete":
                    handleOrganizationDelete(event);
                    break;
                default:
                    log.warn("Unhandled webhook event type: {}", eventType);
            }

            // Acknowledge immediately to prevent retries
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing ScaleKit webhook", e);
            // Still return 200 to prevent ScaleKit from retrying
            // Log the error for manual investigation
            return ResponseEntity.ok().build();
        }
    }

    private void handleUserCreateOrUpdate(JsonNode event) {
        JsonNode userData = event.get("data");
        String userId = userData.get("id").asText();
        String email = userData.has("email") ? userData.get("email").asText() : null;
        String firstName = userData.has("firstName") ? userData.get("firstName").asText() : null;
        String lastName = userData.has("lastName") ? userData.get("lastName").asText() : null;

        // Extract organization ID for tenant-aware sync
        // Webhooks include organization context in the event data
        String organizationId = null;
        if (userData.has("organizationId")) {
            organizationId = userData.get("organizationId").asText();
        }

        // Update or create shadow user
        userRepository.findByScalekitId(userId).ifPresentOrElse(
                existingUser -> {
                    // Update existing
                    if (email != null)
                        existingUser.setEmail(email);
                    if (firstName != null)
                        existingUser.setFirstName(firstName);
                    if (lastName != null)
                        existingUser.setLastName(lastName);
                    userRepository.save(existingUser);
                    log.info("Updated shadow user from webhook: userId={}", userId);
                },
                () -> {
                    // Create new
                    UserEntity newUser = new UserEntity();
                    newUser.setId(userId);
                    newUser.setScalekitId(userId);
                    newUser.setEmail(email != null ? email : "");
                    newUser.setFirstName(firstName);
                    newUser.setLastName(lastName);
                    newUser.setActive(true);
                    userRepository.save(newUser);
                    log.info("Created shadow user from webhook: userId={}", userId);
                });

        // Sync to Permit.io (async) with organization context if available
        if (email != null) {
            permitSyncService.syncUserAsync(userId, email, firstName, lastName, organizationId);
        }
    }

    private void handleUserDelete(JsonNode event) {
        JsonNode userData = event.get("data");
        String userId = userData.get("id").asText();

        // De-provision user in local database
        userRepository.findByScalekitId(userId).ifPresent(user -> {
            user.setActive(false);
            userRepository.save(user);
            log.info("De-provisioned user from webhook: userId={}", userId);
        });

        // Note: Permit.io user deletion would be handled separately
    }

    private void handleOrganizationCreateOrUpdate(JsonNode event) {
        JsonNode orgData = event.get("data");
        String orgId = orgData.get("id").asText();
        String orgName = orgData.has("name") ? orgData.get("name").asText() : orgId;

        // Sync to Permit.io (async)
        permitSyncService.syncOrganizationAsync(orgId, orgName);
    }

    private void handleOrganizationDelete(JsonNode event) {
        // Handle organization deletion if needed
        log.info("Organization delete event received (not yet implemented)");
    }

    /**
     * Verify webhook signature using HMAC-SHA256.
     * 
     * CRITICAL: Uses constant-time comparison to prevent timing attacks.
     */
    private boolean verifySignature(String rawBody, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] computedHash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedSignature = bytesToHex(computedHash);

            // Constant-time comparison
            return constantTimeEquals(computedSignature, signature);
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
