package com.hms.servicename.event;

public record UserCreatedEvent(String userId, String email, String tenantId, String role) {}
