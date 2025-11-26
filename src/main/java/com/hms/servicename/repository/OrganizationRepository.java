package com.hms.servicename.repository;

import com.hms.servicename.model.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String> {
    
    /**
     * Find organization by ScaleKit organization ID.
     */
    Optional<OrganizationEntity> findByScalekitOrgId(String scalekitOrgId);
    
    /**
     * Check if organization exists by ScaleKit organization ID.
     */
    boolean existsByScalekitOrgId(String scalekitOrgId);
}

