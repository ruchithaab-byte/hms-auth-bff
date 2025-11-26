package com.hms.servicename.controller;

import com.hms.lib.common.security.PermitSyncService;
import com.hms.servicename.model.UserEntity;
import com.hms.servicename.repository.UserRepository;
import com.scalekit.ScalekitClient;
import com.scalekit.internal.http.AuthenticationResponse;
import com.scalekit.internal.http.IdTokenClaims;
import com.scalekit.internal.http.AuthenticationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * OIDC callback handler with Just-In-Time (JIT) provisioning.
 * 
 * When a user logs in for the first time, this creates a shadow user
 * in the local database if one doesn't exist.
 */
@RestController
public class OidcCallbackController {

    private static final Logger log = LoggerFactory.getLogger(OidcCallbackController.class);

    @Autowired
    private ScalekitClient scalekitClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermitSyncService permitSyncService;

    @Value("${scalekit.client.id}")
    private String clientId;

    /**
     * Custom OIDC callback handler with JIT provisioning.
     * 
     * Steps:
     * 1. Exchange the code for tokens using ScalekitClient
     * 2. Extract user claims from the ID token
     * 3. Perform Just-in-Time (JIT) provisioning (create/update local user)
     * 4. Sync to Permit.io if not already synced
     * 5. Establish session/cookie
     */
    @GetMapping("/api/callback")
    @Transactional
    public void handleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        try {
            // 1. Exchange code for tokens using ScaleKit SDK
            AuthenticationOptions options = new AuthenticationOptions();
            AuthenticationResponse authResult = scalekitClient.authentication()
                    .authenticateWithCode(code, getRedirectUri(request), options);

            // 2. Extract claims from ID token
            IdTokenClaims idTokenClaims = authResult.getIdTokenClaims();

            String userId = idTokenClaims.getId();
            String email = idTokenClaims.getEmail();
            String firstName = idTokenClaims.getGivenName();
            String lastName = idTokenClaims.getFamilyName();

            if (userId == null) {
                log.error("User ID not found in token claims");
                response.sendRedirect("/login?error=invalid_token");
                return;
            }

            // 3. JIT Provisioning: Create shadow user if not exists
            UserEntity localUser = userRepository.findByScalekitId(userId)
                    .orElseGet(() -> {
                        log.info("Creating shadow user via JIT provisioning: userId={}, email={}", userId, email);
                        UserEntity newUser = new UserEntity();
                        newUser.setId(userId);
                        newUser.setScalekitId(userId);
                        newUser.setEmail(email != null ? email : "");
                        newUser.setFirstName(firstName);
                        newUser.setLastName(lastName);
                        newUser.setActive(true);
                        UserEntity saved = userRepository.save(newUser);

                        // Sync to Permit.io (async, non-blocking)
                        // Note: tenantId is null for JIT provisioning (SSO login doesn't have org
                        // context)
                        // User will be synced globally; can be assigned to tenant later
                        if (email != null) {
                            permitSyncService.syncUserAsync(userId, email, firstName, lastName, null);
                        }

                        return saved;
                    });

            // 4. Create session
            request.getSession().setAttribute("user", localUser);
            request.getSession().setAttribute("userId", userId);

            // 5. Redirect to frontend
            response.sendRedirect("/dashboard");

        } catch (Exception e) {
            log.error("Error handling OIDC callback", e);
            response.sendRedirect("/login?error=callback_failed");
        }
    }

    private String getRedirectUri(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath();
        return String.format("%s://%s:%d%s/api/callback", scheme, host, port, contextPath);
    }
}
