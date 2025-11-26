package com.hms.servicename.config;

import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Force Logback to be used instead of NOP implementation from ScaleKit SDK.
 * This initializer runs before Spring Boot's logging system initialization.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    static {
        // Force Logback by setting system property before any SLF4J initialization
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
        System.setProperty("logback.configurationFile", "logback-spring.xml");
        
        // Ensure Logback's StaticLoggerBinder is loaded
        try {
            Class<?> logbackContext = Class.forName("ch.qos.logback.classic.LoggerContext");
            LoggerFactory.getILoggerFactory();
        } catch (ClassNotFoundException e) {
            // Logback not found - this should not happen if dependencies are correct
            System.err.println("WARNING: Logback not found in classpath");
        }
    }
    
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // This runs early in Spring Boot initialization
        // The static block above ensures Logback is used
    }
}

