package com.hms.servicename.config;

import com.scalekit.ScalekitClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScalekitConfig {

    @Value("${scalekit.env.url}")
    private String envUrl;

    @Value("${scalekit.client.id}")
    private String clientId;

    @Value("${scalekit.client.secret}")
    private String clientSecret;

    @Bean
    public ScalekitClient scalekitClient() {
        return new ScalekitClient(envUrl, clientId, clientSecret);
    }
}

