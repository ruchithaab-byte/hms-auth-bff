package com.hms.servicename.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Circuit breaker wrapper for ScaleKit SDK calls.
 * 
 * Protects the BFF from ScaleKit API failures/slowdowns.
 * When circuit is open, calls fail-fast without making network requests.
 */
@Service
public class ScaleKitCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ScaleKitCircuitBreaker.class);
    private final CircuitBreaker circuitBreaker;

    public ScaleKitCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)  // Open circuit after 50% failures
                .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait 30s before trying again
                .slidingWindowSize(10)  // Track last 10 calls
                .minimumNumberOfCalls(5)  // Need at least 5 calls before opening
                .permittedNumberOfCallsInHalfOpenState(3)  // Allow 3 calls when half-open
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        this.circuitBreaker = registry.circuitBreaker("scalekit");
        
        // Add event listeners for monitoring
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("ScaleKit Circuit Breaker state changed: {}", event.getStateTransition());
                });
    }

    /**
     * Execute a ScaleKit SDK call with circuit breaker protection.
     * 
     * @param supplier The ScaleKit SDK call to execute
     * @return Result of the call
     * @throws RuntimeException if circuit is open or call fails
     */
    public <T> T execute(Supplier<T> supplier) {
        return circuitBreaker.executeSupplier(supplier);
    }

    /**
     * Execute a ScaleKit SDK call with fallback.
     * 
     * @param supplier The ScaleKit SDK call
     * @param fallback Fallback value if circuit is open or call fails
     * @return Result of call or fallback value
     */
    public <T> T executeWithFallback(Supplier<T> supplier, T fallback) {
        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (Exception e) {
            log.warn("ScaleKit call failed, using fallback", e);
            return fallback;
        }
    }

    /**
     * Get current circuit breaker state for monitoring.
     */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }
}

