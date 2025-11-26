package com.hms.servicename.service;

import com.hms.servicename.event.UserCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserEventProducer {
    private static final Logger log = LoggerFactory.getLogger(UserEventProducer.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserCreated(UserCreatedEvent event) {
        log.info("Publishing UserCreatedEvent for user: {}", event.userId());
        // Topic: "hms.user.created"
        kafkaTemplate.send("hms.user.created", event.userId(), event);
    }
}
