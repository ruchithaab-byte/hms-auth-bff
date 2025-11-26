package com.hms.servicename.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * User role assignment entity.
 * 
 * Tracks role assignments for users in organizations.
 * This is the local shadow of ScaleKit role assignments.
 */
@Entity
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserRoleId.class)
public class UserRoleEntity {

    @Id
    @Column(name = "user_id", length = 255)
    private String userId;

    @Id
    @Column(name = "organization_id", length = 255)
    private String organizationId;

    @Id
    @Column(name = "role_name", length = 255)
    private String roleName;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}

