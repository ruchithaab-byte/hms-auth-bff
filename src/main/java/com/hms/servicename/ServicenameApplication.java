package com.hms.servicename;

import com.hms.servicename.config.LoggingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan(basePackages = {"com.hms.servicename", "com.hms.lib.common"})
@EnableFeignClients(basePackages = "com.hms.bff.client.workflow.api")
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableAsync
@EnableRetry
public class ServicenameApplication {

    public static void main(String[] args) {
        // Force Logback before Spring Boot starts (prevents NOPLoggerFactory from ScaleKit SDK)
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
        System.setProperty("logback.configurationFile", "logback-spring.xml");
        
        SpringApplication app = new SpringApplication(ServicenameApplication.class);
        app.addInitializers(new LoggingConfig());
        app.run(args);
    }
}

