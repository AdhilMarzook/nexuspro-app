package com.nexuspro.controller;

import com.nexuspro.dto.response.ApiResponse;
import com.nexuspro.model.entity.WellbeingEntry;
import com.nexuspro.service.WellbeingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wellbeing")
@RequiredArgsConstructor
public class WellbeingController {

    private final WellbeingService wellbeingService;

    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<WellbeingEntry>> logEntry(
            @Valid @RequestBody WellbeingEntry entry,
            @RequestHeader("X-User-Id") String userId) {
        WellbeingEntry saved = wellbeingService.logEntry(UUID.fromString(userId), entry);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved));
    }

    @PutMapping("/entries/{date}")
    public ResponseEntity<ApiResponse<WellbeingEntry>> updateEntry(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody WellbeingEntry updates,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
            wellbeingService.updateEntry(UUID.fromString(userId), date, updates)));
    }

    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<Page<WellbeingEntry>>> getHistory(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            wellbeingService.getHistory(UUID.fromString(userId), pageable)));
    }

    @GetMapping("/entries/today")
    public ResponseEntity<ApiResponse<WellbeingEntry>> getToday(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
            wellbeingService.getTodaysEntry(UUID.fromString(userId))));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "30") int days) {
        if (days < 7 || days > 365)
            return ResponseEntity.badRequest().body(ApiResponse.error("days must be 7–365", "INVALID_PARAM"));
        return ResponseEntity.ok(ApiResponse.ok(
            wellbeingService.getSummary(UUID.fromString(userId), days)));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<String>> getRecommendations(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
            wellbeingService.getAiRecommendations(UUID.fromString(userId))));
    }
}
