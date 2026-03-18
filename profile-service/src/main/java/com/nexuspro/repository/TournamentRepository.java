package com.nexuspro.repository;

import com.nexuspro.model.entity.Tournament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, UUID> {

    Page<Tournament> findByProfileIdOrderByEventDateDesc(UUID profileId, Pageable pageable);

    long countByProfileId(UUID profileId);

    long countByProfileIdAndVerified(UUID profileId, boolean verified);

    @Query("SELECT COUNT(t) FROM Tournament t WHERE t.profileId = :profileId AND t.placement IN ('1st', '2nd', '3rd', '1', '2', '3')")
    int countTop3Placements(@Param("profileId") UUID profileId);
}
