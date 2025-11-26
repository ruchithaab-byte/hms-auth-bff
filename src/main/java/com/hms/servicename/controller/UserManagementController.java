package com.hms.servicename.controller;

import com.hms.servicename.model.OrganizationEntity;
import com.hms.servicename.model.UserEntity;
import com.hms.servicename.service.UserManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for user and organization management.
 * 
 * This controller handles the "White-Label Identity" pattern where
 * all user/org/role management happens via HMS UI, not ScaleKit dashboard.
 */
@RestController
@RequestMapping("/api/admin")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    /**
     * Create a new user in an organization.
     * 
     * Flow: ScaleKit → Local DB → Permit.io (async)
     */
    @PostMapping("/organizations/{organizationId}/users")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<Map<String, Object>> createUser(
            @PathVariable String organizationId,
            @RequestBody CreateUserRequest request) {
        UserEntity user = userManagementService.createUser(
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                organizationId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName", user.getLastName() != null ? user.getLastName() : ""));
    }

    /**
     * Create a new organization.
     */
    @PostMapping("/organizations")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<Map<String, Object>> createOrganization(@RequestBody CreateOrganizationRequest request) {
        OrganizationEntity org = userManagementService.createOrganization(
                request.getName(),
                request.getExternalId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", org.getId(),
                "name", org.getName()));
    }

    /**
     * Assign a role to a user in an organization.
     */
    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<Void> assignRole(
            @PathVariable String userId,
            @RequestBody AssignRoleRequest request) {

        userManagementService.assignRole(userId, request.getRoleName(), request.getOrgId());
        return ResponseEntity.ok().build();
    }

    /**
     * List users in an organization (with multi-tenancy enforcement).
     */
    @GetMapping("/organizations/{orgId}/users")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<List<Map<String, Object>>> listUsers(@PathVariable String orgId) {
        List<UserEntity> users = userManagementService.listUsers(orgId);

        List<Map<String, Object>> userList = users.stream()
                .map(user -> {
                    Map<String, Object> userMap = new java.util.HashMap<>();
                    userMap.put("id", user.getId());
                    userMap.put("email", user.getEmail());
                    userMap.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
                    userMap.put("lastName", user.getLastName() != null ? user.getLastName() : "");
                    userMap.put("active", user.getActive());
                    return userMap;
                })
                .toList();

        return ResponseEntity.ok(userList);
    }

    /**
     * Get user details by ID.
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String userId) {
        UserEntity user = userManagementService.getUser(userId);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName", user.getLastName() != null ? user.getLastName() : "",
                "active", user.getActive()));
    }

    // Request/Response DTOs
    public static class CreateUserRequest {
        private String email;
        private String firstName;
        private String lastName;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    public static class CreateOrganizationRequest {
        private String name;
        private String externalId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getExternalId() {
            return externalId;
        }

        public void setExternalId(String externalId) {
            this.externalId = externalId;
        }
    }

    public static class AssignRoleRequest {
        private String roleName;
        private String orgId;

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }
    }
}
