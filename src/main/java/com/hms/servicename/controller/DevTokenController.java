package com.hms.servicename.controller;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only controller to mint JWTs for testing.
 * Only active when not in production (conceptually, though here we just add it).
 */
@RestController
@RequestMapping("/dev/token")
public class DevTokenController {

    // Must match the secret used in SecurityConfig
    private static final String DEV_SECRET = "dev-secret-key-for-testing-only-1234567890";

    @PostMapping
    public ResponseEntity<Map<String, String>> generateToken(
            @RequestParam(defaultValue = "admin@hospital.com") String email,
            @RequestParam(defaultValue = "SCOPE_admin") String scope,
            @RequestParam(defaultValue = "org_123") String orgId) {

        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(email) // Use email as subject for simplicity in dev
                    .issuer("http://localhost:8080")
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("scope", scope)
                    .claim("org_id", orgId)
                    .claim("email", email)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );

            signedJWT.sign(new MACSigner(DEV_SECRET));

            return ResponseEntity.ok(Map.of("token", signedJWT.serialize()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
