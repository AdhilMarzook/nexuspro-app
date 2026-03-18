package com.nexuspro.repository;

import com.nexuspro.model.entity.CareerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {
    Optional<CareerProfile> findByUserId(UUID userId);
    Optional<CareerProfile> findByUsername(String username);
    boolean existsByUserId(UUID userId);
    boolean existsByUsername(String username);
}
