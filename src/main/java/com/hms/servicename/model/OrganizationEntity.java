package com.hms.servicename.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Shadow organization entity for local Postgres database.
 * 
 * Maps to ScaleKit organizations and Permit.io workspaces.
 * The primary key is the ScaleKit organization ID.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity {

    @Id
    @Column(name = "id", length = 255)
    private String id;  // ScaleKit organization ID (primary key)

    @Column(name = "scalekit_org_id", unique = true, nullable = false, length = 255)
    private String scalekitOrgId;  // Redundant but explicit

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

