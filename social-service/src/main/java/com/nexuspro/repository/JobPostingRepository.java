package com.nexuspro.repository;

import com.nexuspro.model.entity.JobPosting;
import com.nexuspro.model.entity.JobPosting.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    Page<JobPosting> findByStatusOrderByCreatedAtDesc(Status status, Pageable pageable);

    Page<JobPosting> findByPosterIdOrderByCreatedAtDesc(UUID posterId, Pageable pageable);

    @Query("""
        SELECT j FROM JobPosting j
        WHERE j.status = 'ACTIVE'
          AND (:jobType   IS NULL OR j.jobType = :jobType)
          AND (:expLevel  IS NULL OR j.experienceLevel = :expLevel)
          AND (:remote    IS NULL OR j.remote = :remote)
          AND (:keyword   IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%',:keyword,'%'))
                                  OR LOWER(j.description) LIKE LOWER(CONCAT('%',:keyword,'%'))
                                  OR LOWER(j.organisation) LIKE LOWER(CONCAT('%',:keyword,'%')))
        ORDER BY j.createdAt DESC
        """)
    Page<JobPosting> searchJobs(
        String jobType, String expLevel, Boolean remote, String keyword, Pageable pageable);
}
