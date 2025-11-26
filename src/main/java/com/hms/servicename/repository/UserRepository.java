package com.hms.servicename.repository;

import com.hms.servicename.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    
    /**
     * Find user by ScaleKit ID.
     */
    Optional<UserEntity> findByScalekitId(String scalekitId);
    
    /**
     * Find user by email.
     */
    Optional<UserEntity> findByEmail(String email);
    
    /**
     * Check if user exists by ScaleKit ID.
     */
    boolean existsByScalekitId(String scalekitId);
}

