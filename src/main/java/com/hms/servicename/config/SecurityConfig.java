package com.hms.servicename.config;

import com.hms.lib.common.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * @Order(1) - Stateless Resource Server for APIs
     * This chain is ALWAYS ON. It protects all internal /api/ routes.
     * It demands a valid JWT Bearer token and is STATELESS. [cite: 1028, 1032,
     * 1194]
     */
    @Bean
    @Order(1)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Apply this filter chain to API routes
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authz -> authz
                        // Webhooks must be public but secured separately
                        .requestMatchers("/api/webhooks/**").permitAll()
                        // TODO: Remove this permitAll once frontend authentication is implemented
                        .requestMatchers("/api/auth/onboarding/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .csrf(csrf -> csrf.disable()); // Disable CSRF for stateless APIs
        return http.build();
    }

    @Bean
    public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
        // For DEV testing: Use a local secret instead of ScaleKit's JWK Set
        // This allows us to use tokens minted by DevTokenController
        String secret = "dev-secret-key-for-testing-only-1234567890";
        javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256");
        return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withSecretKey(key).build();
    }

    /**
     * @Order(2) - Stateful OIDC Client for User Login
     * This chain is ONLY for the 'saas-backend-for-frontend' pattern.
     * It handles the user-facing login flow and is STATEFUL. [cite: 974, 1191]
     */

    @Bean
    @Order(2)
    public SecurityFilterChain statefulLoginFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/", "/login", "/public/**", "/dev/**").permitAll()
                        .anyRequest().authenticated())
                // This is the key: enable the stateful OpenID Connect login flow [cite: 981]
                .oauth2Login(withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/dev/**"));
        return http.build();
    }

}
