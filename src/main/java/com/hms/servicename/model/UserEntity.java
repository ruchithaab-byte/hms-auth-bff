package com.hms.servicename.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Shadow user entity for local Postgres database.
 * 
 * CRITICAL: This table enables referential integrity (FK constraints) in business tables.
 * For example, a `projects` table can have `created_by_user_id` FK referencing this table.
 * 
 * The primary key is the ScaleKit user ID to maintain 1:1 mapping.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @Column(name = "id", length = 255)
    private String id;  // ScaleKit user ID (primary key)

    @Column(name = "scalekit_id", unique = true, nullable = false, length = 255)
    private String scalekitId;  // Redundant but explicit

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

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

