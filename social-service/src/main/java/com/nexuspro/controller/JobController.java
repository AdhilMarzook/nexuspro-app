package com.nexuspro.controller;

import com.nexuspro.dto.response.ApiResponse;
import com.nexuspro.model.entity.JobApplication;
import com.nexuspro.model.entity.JobPosting;
import com.nexuspro.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // ── JOB LISTINGS ─────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobPosting>>> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String expLevel,
            @RequestParam(required = false) Boolean remote,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            jobService.searchJobs(keyword, jobType, expLevel, remote, pageable)));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobPosting>> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJob(jobId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobPosting>> createJob(
            @Valid @RequestBody JobPosting job,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        // Only orgs and coaches can post jobs
        if (!role.equals("ORG_ADMIN") && !role.equals("COACH") && !role.equals("PLATFORM_ADMIN"))
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only organisations and coaches can post jobs", "FORBIDDEN"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(jobService.createJob(UUID.fromString(userId), job)));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobPosting>> updateJob(
            @PathVariable UUID jobId,
            @Valid @RequestBody JobPosting updates,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
            jobService.updateJob(jobId, UUID.fromString(userId), updates)));
    }

    @PatchMapping("/{jobId}/close")
    public ResponseEntity<ApiResponse<Void>> closeJob(
            @PathVariable UUID jobId,
            @RequestHeader("X-User-Id") String userId) {
        jobService.closeJob(jobId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(null, "Job closed successfully"));
    }

    // ── APPLICATIONS ──────────────────────────────────────────────────────────

    @PostMapping("/{jobId}/apply")
    public ResponseEntity<ApiResponse<JobApplication>> apply(
            @PathVariable UUID jobId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) {
        JobApplication app = jobService.apply(
            jobId, UUID.fromString(userId),
            body.get("coverLetter"),
            body.get("cvUrl"),
            body.get("portfolioUrl")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(app,
            "Application submitted successfully"));
    }

    @PatchMapping("/applications/{applicationId}/withdraw")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") String userId) {
        jobService.withdrawApplication(applicationId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(null, "Application withdrawn"));
    }

    @GetMapping("/my-applications")
    public ResponseEntity<ApiResponse<Page<JobApplication>>> myApplications(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            jobService.getMyApplications(UUID.fromString(userId), pageable)));
    }

    @GetMapping("/{jobId}/applications")
    public ResponseEntity<ApiResponse<Page<JobApplication>>> getApplications(
            @PathVariable UUID jobId,
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            jobService.getApplicationsForJob(jobId, UUID.fromString(userId), pageable)));
    }

    @PatchMapping("/applications/{applicationId}/status")
    public ResponseEntity<ApiResponse<JobApplication>> updateStatus(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) {
        JobApplication.Status newStatus = JobApplication.Status.valueOf(body.get("status"));
        return ResponseEntity.ok(ApiResponse.ok(
            jobService.updateApplicationStatus(
                applicationId, UUID.fromString(userId), newStatus, body.get("notes"))));
    }
}
