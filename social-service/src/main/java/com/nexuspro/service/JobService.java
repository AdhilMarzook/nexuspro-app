package com.nexuspro.service;

import com.nexuspro.exception.BusinessException;
import com.nexuspro.exception.DuplicateResourceException;
import com.nexuspro.exception.ResourceNotFoundException;
import com.nexuspro.model.entity.JobApplication;
import com.nexuspro.model.entity.JobApplication.Status;
import com.nexuspro.model.entity.JobPosting;
import com.nexuspro.repository.JobApplicationRepository;
import com.nexuspro.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Job board service — post jobs, search, apply, manage applications.
 *
 * Security:
 * - Only ORG_ADMIN and COACH roles can post jobs
 * - Applicants can only see their own applications
 * - Recruiters can only see applications for their own postings
 * - Recruiter notes are never returned to applicants
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobPostingRepository    jobRepo;
    private final JobApplicationRepository appRepo;
    private final KafkaTemplate<String, String> kafka;

    // ── JOB POSTINGS ───────────────────────────────────────────────────────

    @Transactional
    public JobPosting createJob(UUID posterId, JobPosting job) {
        job.setId(null);          // Ensure ID is auto-generated
        job.setPosterId(posterId);
        job.setStatus(JobPosting.Status.ACTIVE);
        job.setApplicationCount(0);
        job.setViewCount(0);

        if (job.getExpiresAt() == null) {
            job.setExpiresAt(Instant.now().plus(60, ChronoUnit.DAYS));
        }

        // Sanitise free-text fields
        job.setTitle(sanitise(job.getTitle()));
        job.setDescription(sanitise(job.getDescription()));
        job.setRequirements(sanitise(job.getRequirements()));
        job.setBenefits(sanitise(job.getBenefits()));
        job.setOrganisation(sanitise(job.getOrganisation()));

        return jobRepo.save(job);
    }

    public Page<JobPosting> searchJobs(String keyword, String jobType, String expLevel,
                                       Boolean remote, Pageable pageable) {
        return jobRepo.searchJobs(jobType, expLevel, remote, keyword, pageable);
    }

    public Page<JobPosting> getActiveJobs(Pageable pageable) {
        return jobRepo.findByStatusOrderByCreatedAtDesc(JobPosting.Status.ACTIVE, pageable);
    }

    public JobPosting getJob(UUID jobId) {
        JobPosting job = jobRepo.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));

        // Increment views async
        jobRepo.incrementViews(jobId);
        return job;
    }

    @Transactional
    public JobPosting updateJob(UUID jobId, UUID posterId, JobPosting updates) {
        JobPosting existing = jobRepo.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));

        if (!existing.getPosterId().equals(posterId))
            throw new BusinessException("Not authorised to edit this job", "FORBIDDEN");

        existing.setTitle(sanitise(updates.getTitle()));
        existing.setDescription(sanitise(updates.getDescription()));
        existing.setRequirements(sanitise(updates.getRequirements()));
        existing.setBenefits(sanitise(updates.getBenefits()));
        existing.setSalaryMin(updates.getSalaryMin());
        existing.setSalaryMax(updates.getSalaryMax());
        existing.setRemote(updates.isRemote());
        existing.setLocation(sanitise(updates.getLocation()));
        existing.setTags(updates.getTags());
        existing.setGameTitles(updates.getGameTitles());
        existing.setExpiresAt(updates.getExpiresAt());

        return jobRepo.save(existing);
    }

    @Transactional
    public void closeJob(UUID jobId, UUID posterId) {
        JobPosting job = jobRepo.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));

        if (!job.getPosterId().equals(posterId))
            throw new BusinessException("Not authorised to close this job", "FORBIDDEN");

        job.setStatus(JobPosting.Status.FILLED);
        jobRepo.save(job);
    }

    // ── APPLICATIONS ───────────────────────────────────────────────────────

    @Transactional
    public JobApplication apply(UUID jobId, UUID applicantId, String coverLetter, String cvUrl, String portfolioUrl) {
        JobPosting job = jobRepo.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));

        if (job.getStatus() != JobPosting.Status.ACTIVE)
            throw new BusinessException("This job is no longer accepting applications", "JOB_CLOSED");

        if (appRepo.existsByJobIdAndApplicantId(jobId, applicantId))
            throw new DuplicateResourceException("You have already applied for this job");

        // Validate CV URL is a real S3/storage link
        if (cvUrl != null && !cvUrl.isBlank() && !isValidStorageUrl(cvUrl))
            throw new BusinessException("Invalid CV URL", "INVALID_URL");

        JobApplication app = JobApplication.builder()
            .jobId(jobId)
            .applicantId(applicantId)
            .coverLetter(coverLetter != null ? sanitise(coverLetter) : null)
            .cvUrl(cvUrl)
            .portfolioUrl(portfolioUrl)
            .build();

        app = appRepo.save(app);

        // Increment application count
        jobRepo.incrementApplicationCount(jobId);

        // Notify job poster
        kafka.send("jobs.application.received",
            "{\"jobId\":\"" + jobId + "\",\"applicantId\":\"" + applicantId
                + "\",\"posterId\":\"" + job.getPosterId() + "\"}");

        log.info("Job application submitted: jobId={} applicantId={}", jobId, applicantId);
        return app;
    }

    @Transactional
    public void withdrawApplication(UUID applicationId, UUID applicantId) {
        JobApplication app = appRepo.findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));

        if (!app.getApplicantId().equals(applicantId))
            throw new BusinessException("Not authorised to withdraw this application", "FORBIDDEN");

        if (app.getStatus() == Status.ACCEPTED)
            throw new BusinessException("Cannot withdraw an accepted application. Please contact the employer.", "ALREADY_ACCEPTED");

        app.setStatus(Status.WITHDRAWN);
        appRepo.save(app);
    }

    // Applicant: see own applications
    public Page<JobApplication> getMyApplications(UUID applicantId, Pageable pageable) {
        return appRepo.findByApplicantIdOrderByAppliedAtDesc(applicantId, pageable);
    }

    // Recruiter: see applications for their job — recruiter notes excluded by projection
    public Page<JobApplication> getApplicationsForJob(UUID jobId, UUID recruiterId, Pageable pageable) {
        JobPosting job = jobRepo.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));
        if (!job.getPosterId().equals(recruiterId))
            throw new BusinessException("Not authorised to view these applications", "FORBIDDEN");

        return appRepo.findByJobIdOrderByAppliedAtDesc(jobId, pageable);
    }

    @Transactional
    public JobApplication updateApplicationStatus(UUID applicationId, UUID recruiterId,
                                                  Status newStatus, String notes) {
        JobApplication app = appRepo.findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));

        // Verify recruiter owns the job
        JobPosting job = jobRepo.findById(app.getJobId())
            .orElseThrow(() -> new ResourceNotFoundException("Job", app.getJobId().toString()));
        if (!job.getPosterId().equals(recruiterId))
            throw new BusinessException("Not authorised", "FORBIDDEN");

        app.setStatus(newStatus);
        app.setReviewedAt(Instant.now());
        if (notes != null) app.setRecruiterNotes(sanitise(notes));
        app = appRepo.save(app);

        // Notify applicant of status change
        kafka.send("jobs.application.status_changed",
            "{\"applicationId\":\"" + applicationId + "\",\"applicantId\":\""
                + app.getApplicantId() + "\",\"newStatus\":\"" + newStatus + "\"}");

        return app;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String sanitise(String input) {
        if (input == null) return null;
        return input.replaceAll("<[^>]*>", "").trim();
    }

    private boolean isValidStorageUrl(String url) {
        return url.startsWith("https://nexuspro-storage.s3.") ||
               url.startsWith("https://storage.nexuspro.co.uk/");
    }
}
