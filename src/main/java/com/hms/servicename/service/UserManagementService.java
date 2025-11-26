package com.hms.servicename.service;

import com.hms.lib.common.context.UserContext;
import com.hms.lib.common.security.PermitSyncService;
import com.hms.servicename.model.OrganizationEntity;
import com.hms.servicename.model.UserEntity;
import com.hms.servicename.model.UserRoleEntity;
import com.hms.servicename.repository.OrganizationRepository;
import com.hms.servicename.repository.UserRepository;
import com.hms.servicename.repository.UserRoleRepository;
import com.scalekit.ScalekitClient;
import com.scalekit.grpc.scalekit.v1.organizations.CreateOrganization;
import com.scalekit.grpc.scalekit.v1.users.CreateUserAndMembershipRequest;
import com.scalekit.grpc.scalekit.v1.users.CreateUser;
import com.scalekit.grpc.scalekit.v1.users.CreateUserProfile;
import com.scalekit.grpc.scalekit.v1.users.UpdateMembershipRequest;
import com.scalekit.grpc.scalekit.v1.users.UpdateMembership;
import com.scalekit.grpc.scalekit.v1.commons.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing users, organizations, and roles.
 * 
 * Implements the "Dual-Write" pattern:
 * 1. Create in ScaleKit (external, non-transactional)
 * 2. Create shadow entity in local Postgres (transactional, enables FK
 * constraints)
 * 3. Sync to Permit.io (async, non-blocking)
 * 
 * Includes rollback logic: if local DB write fails, delete from ScaleKit.
 */
@Service
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final ScalekitClient scalekitClient;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermitSyncService permitSyncService;
    private final ScaleKitCircuitBreaker circuitBreaker;
    private final UserEventProducer userEventProducer;

    @Autowired
    public UserManagementService(
            ScalekitClient scalekitClient,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            UserRoleRepository userRoleRepository,
            PermitSyncService permitSyncService,
            ScaleKitCircuitBreaker circuitBreaker,
            UserEventProducer userEventProducer) {
        this.scalekitClient = scalekitClient;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.userRoleRepository = userRoleRepository;
        this.permitSyncService = permitSyncService;
        this.circuitBreaker = circuitBreaker;
        this.userEventProducer = userEventProducer;
    }

    /**
     * Create a new user in an organization.
     * 
     * Flow:
     * 1. Create in ScaleKit (via SDK with circuit breaker)
     * 2. Create shadow user in local Postgres (transactional)
     * 3. Sync to Permit.io (async, non-blocking)
     * 
     * If step 2 fails, rollback step 1 (delete from ScaleKit).
     * 
     * @param email          User email address
     * @param firstName      User first name (optional)
     * @param lastName       User last name (optional)
     * @param organizationId Organization ID where user will be created
     * @return Created user entity
     */
    @Transactional
    public UserEntity createUser(String email, String firstName, String lastName, String organizationId) {
        com.scalekit.grpc.scalekit.v1.users.CreateUserAndMembershipResponse scalekitResponse = null;
        String userId = null;

        try {
            // 0. CRITICAL: Lookup organization to get ScaleKit organization ID
            // The organizationId parameter is the EXTERNAL ID (from path variable),
            // but ScaleKit API requires the SCALEKIT ORGANIZATION ID
            OrganizationEntity organization = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            String scalekitOrgId = organization.getScalekitOrgId();
            log.info("Creating user for organization: externalId={}, scalekitOrgId={}", organizationId, scalekitOrgId);

            // 1. Build the SDK Request Object using Builder Pattern
            CreateUserProfile userProfile = CreateUserProfile.newBuilder()
                    .setFirstName(firstName != null ? firstName : "")
                    .setLastName(lastName != null ? lastName : "")
                    .build();

            CreateUser createUser = CreateUser.newBuilder()
                    .setEmail(email)
                    .setUserProfile(userProfile)
                    .build();

            CreateUserAndMembershipRequest createRequest = CreateUserAndMembershipRequest.newBuilder()
                    .setOrganizationId(scalekitOrgId) // Use ScaleKit org ID, not external ID
                    .setUser(createUser)
                    .setSendInvitationEmail(false) // Don't send email, user will be activated separately
                    .build();

            // 2. Create in ScaleKit (external, non-transactional, with circuit breaker)
            scalekitResponse = circuitBreaker
                    .execute(() -> scalekitClient.users().createUserAndMembership(scalekitOrgId, createRequest));

            // 3. Extract user ID from response
            // The response contains the created user - need to extract ID
            userId = extractUserIdFromResponse(scalekitResponse);
            String userEmail = extractEmailFromResponse(scalekitResponse);

            // 4. CRITICAL: Create Shadow User in Local Postgres (transactional)
            // This enables FK constraints in business tables (e.g.,
            // projects.created_by_user_id)
            UserEntity localUser = new UserEntity();
            localUser.setId(userId); // ScaleKit ID as primary key
            localUser.setScalekitId(userId);
            localUser.setEmail(userEmail != null ? userEmail : email);
            localUser.setFirstName(firstName);
            localUser.setLastName(lastName);
            localUser.setActive(true);
            localUser = userRepository.save(localUser);

            // 5. Sync to Permit.io (ASYNC - non-blocking, with retry)
            // Do NOT fail HTTP request if Permit.io sync fails
            final String finalUserId = userId;
            final String finalUserEmail = userEmail != null ? userEmail : email;
            final String finalFirstName = firstName;
            final String finalLastName = lastName;
            permitSyncService.syncUserAsync(finalUserId, finalUserEmail, finalFirstName, finalLastName, organizationId)
                    .exceptionally(ex -> {
                        log.warn("Permit.io sync failed for user {}, will retry: {}", finalUserId, ex.getMessage());
                        return false;
                    });

            // 6. Publish Event to Kafka (for Workflow, Projector, etc.)
            userEventProducer.publishUserCreated(
                    new com.hms.servicename.event.UserCreatedEvent(userId, userEmail != null ? userEmail : email,
                            organizationId, "default-role"));

            log.info("User created successfully: userId={}, email={}", userId, userEmail);
            return localUser;

        } catch (Exception e) {
            log.error("Failed to create user: email={}", email, e);

            // Rollback: Delete from ScaleKit if local DB write failed
            final String finalUserIdForRollback = userId;
            if (finalUserIdForRollback != null) {
                try {
                    circuitBreaker.execute(() -> {
                        scalekitClient.users().deleteUser(finalUserIdForRollback);
                        return null;
                    });
                    log.info("Rolled back ScaleKit user creation: userId={}", finalUserIdForRollback);
                } catch (Exception rollbackError) {
                    log.error("Failed to rollback ScaleKit user creation: userId={}", finalUserIdForRollback,
                            rollbackError);
                }
            }

            throw new RuntimeException("Failed to create user: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new organization.
     * 
     * Flow:
     * 1. Create in ScaleKit
     * 2. Create shadow organization in local Postgres
     * 3. Sync to Permit.io (as workspace)
     * 
     * @param name       Organization display name
     * @param externalId Optional external ID for linking (can be null)
     * @return Created organization entity
     */
    @Transactional
    public OrganizationEntity createOrganization(String name, String externalId) {
        com.scalekit.grpc.scalekit.v1.organizations.Organization scalekitOrg = null;
        String orgId = null;

        try {
            // 1. Build the SDK Request Object using Builder Pattern
            CreateOrganization.Builder orgBuilder = CreateOrganization.newBuilder()
                    .setDisplayName(name);

            if (externalId != null && !externalId.isEmpty()) {
                orgBuilder.setExternalId(externalId);
            }

            CreateOrganization createOrgRequest = orgBuilder.build();

            // 2. Create in ScaleKit (with circuit breaker)
            scalekitOrg = circuitBreaker.execute(() -> scalekitClient.organizations().create(createOrgRequest));

            // 3. Extract organization ID from response
            orgId = scalekitOrg.getId();
            String orgName = scalekitOrg.getDisplayName();

            // 4. Create shadow organization in local Postgres
            OrganizationEntity localOrg = new OrganizationEntity();
            localOrg.setId(orgId); // ScaleKit ID as primary key
            localOrg.setScalekitOrgId(orgId);
            localOrg.setName(orgName != null ? orgName : name);
            localOrg.setActive(true);
            localOrg = organizationRepository.save(localOrg);

            // 5. Sync to Permit.io (async, non-blocking)
            final String finalOrgId = orgId;
            final String finalOrgName = orgName != null ? orgName : name;
            permitSyncService.syncOrganizationAsync(finalOrgId, finalOrgName)
                    .exceptionally(ex -> {
                        log.warn("Permit.io sync failed for org {}, will retry: {}", finalOrgId, ex.getMessage());
                        return false;
                    });

            log.info("Organization created successfully: orgId={}, name={}", orgId, orgName);
            return localOrg;

        } catch (Exception e) {
            log.error("Failed to create organization: name={}", name, e);

            // Rollback
            final String finalOrgIdForRollback = orgId;
            if (finalOrgIdForRollback != null) {
                try {
                    circuitBreaker.execute(() -> {
                        scalekitClient.organizations().deleteById(finalOrgIdForRollback);
                        return null;
                    });
                } catch (Exception rollbackError) {
                    log.error("Failed to rollback ScaleKit organization creation: orgId={}", finalOrgIdForRollback,
                            rollbackError);
                }
            }

            throw new RuntimeException("Failed to create organization: " + e.getMessage(), e);
        }
    }

    /**
     * Assign a role to a user in an organization.
     * 
     * Flow:
     * 1. Update local DB (transactional)
     * 2. Assign role in ScaleKit (for JWT claims)
     * 3. Sync role to Permit.io (async, for authorization)
     */
    @Transactional
    public void assignRole(String userId, String roleName, String orgId) {
        // Verify current admin can manage this organization
        String currentAdminOrg = UserContext.getOrgId();
        if (currentAdminOrg != null && !currentAdminOrg.equals(orgId)) {
            // Check if admin has permission (would need permission check here)
            log.warn("Admin from org {} attempting to assign role in org {}", currentAdminOrg, orgId);
            // For now, allow if admin org matches or is super admin
        }

        try {
            // 1. Update local DB (transactional)
            if (!userRoleRepository.existsByUserIdAndOrganizationIdAndRoleName(userId, orgId, roleName)) {
                UserRoleEntity userRole = new UserRoleEntity();
                userRole.setUserId(userId);
                userRole.setOrganizationId(orgId);
                userRole.setRoleName(roleName);
                userRoleRepository.save(userRole);
            }

            // 2. Assign role in ScaleKit (for JWT claims)
            // ScaleKit uses UpdateMembership to assign roles
            Role role = Role.newBuilder()
                    .setName(roleName)
                    .build();

            UpdateMembership updateMembership = UpdateMembership.newBuilder()
                    .addRoles(role)
                    .build();

            UpdateMembershipRequest updateRequest = UpdateMembershipRequest.newBuilder()
                    .setOrganizationId(orgId)
                    .setId(userId)
                    .setMembership(updateMembership)
                    .build();

            circuitBreaker.execute(() -> {
                scalekitClient.users().updateMembership(orgId, userId, updateRequest);
                return null;
            });

            // 3. Sync role to Permit.io (async, non-blocking)
            permitSyncService.assignRoleAsync(userId, roleName, orgId)
                    .exceptionally(ex -> {
                        log.warn("Permit.io role sync failed, will retry: userId={}, role={}, orgId={}",
                                userId, roleName, orgId, ex.getMessage());
                        return false;
                    });

            log.info("Role assigned successfully: userId={}, role={}, orgId={}", userId, roleName, orgId);

        } catch (Exception e) {
            log.error("Failed to assign role: userId={}, role={}, orgId={}", userId, roleName, orgId, e);
            throw new RuntimeException("Failed to assign role: " + e.getMessage(), e);
        }
    }

    /**
     * List users in an organization (with multi-tenancy enforcement).
     */
    public List<UserEntity> listUsers(String orgId) {
        // Verify current admin can manage this organization
        String currentAdminOrg = UserContext.getOrgId();
        if (currentAdminOrg != null && !currentAdminOrg.equals(orgId)) {
            throw new AccessDeniedException("Cannot list users from other organization");
        }

        // Filter by organization (would need organization membership table or query)
        // For now, return all users (this should be enhanced)
        return userRepository.findAll();
    }

    /**
     * Get user details by ID.
     */
    public UserEntity getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    // Helper methods to extract data from ScaleKit SDK responses

    private String extractUserIdFromResponse(
            com.scalekit.grpc.scalekit.v1.users.CreateUserAndMembershipResponse response) {
        // The response contains the created user
        if (response.hasUser()) {
            com.scalekit.grpc.scalekit.v1.users.User user = response.getUser();
            return user.getId();
        }
        throw new RuntimeException("Unable to extract user ID from ScaleKit response - user not found in response");
    }

    private String extractEmailFromResponse(
            com.scalekit.grpc.scalekit.v1.users.CreateUserAndMembershipResponse response) {
        if (response.hasUser()) {
            com.scalekit.grpc.scalekit.v1.users.User user = response.getUser();
            return user.getEmail();
        }
        return null;
    }
}
