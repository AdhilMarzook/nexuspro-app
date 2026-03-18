package com.nexuspro.repository;

import com.nexuspro.model.entity.Certification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    Page<Certification> findByProfileIdOrderByIssueDateDesc(UUID profileId, Pageable pageable);

    long countByProfileId(UUID profileId);
}
