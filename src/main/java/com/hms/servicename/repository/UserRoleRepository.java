package com.hms.servicename.repository;

import com.hms.servicename.model.UserRoleEntity;
import com.hms.servicename.model.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {
    
    /**
     * Find all roles for a user in an organization.
     */
    List<UserRoleEntity> findByUserIdAndOrganizationId(String userId, String organizationId);
    
    /**
     * Find all users with a specific role in an organization.
     */
    List<UserRoleEntity> findByOrganizationIdAndRoleName(String organizationId, String roleName);
    
    /**
     * Delete all roles for a user in an organization.
     */
    void deleteByUserIdAndOrganizationId(String userId, String organizationId);
    
    /**
     * Delete a specific role for a user in an organization.
     */
    void deleteByUserIdAndOrganizationIdAndRoleName(String userId, String organizationId, String roleName);
    
    /**
     * Check if user has a specific role in an organization.
     */
    boolean existsByUserIdAndOrganizationIdAndRoleName(String userId, String organizationId, String roleName);
}

