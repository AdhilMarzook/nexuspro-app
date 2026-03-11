package com.nexuspro.controller;

import com.nexuspro.dto.response.ApiResponse;
import com.nexuspro.model.entity.Contract;
import com.nexuspro.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping(value = "/analyse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Contract>> uploadAndAnalyse(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "context", required = false, defaultValue = "UK-based esports player") String context,
            @RequestHeader("X-User-Id") String userId) {
        Contract contract = contractService.uploadAndAnalyse(file, userId, context);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(contract,
            "Contract uploaded. Analysis will complete within 30 seconds."));
    }

    @GetMapping("/{contractId}")
    public ResponseEntity<ApiResponse<Contract>> getContract(
            @PathVariable UUID contractId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(contractService.getContract(contractId, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Contract>>> getMyContracts(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(contractService.getUserContracts(userId, pageable)));
    }

    @DeleteMapping("/{contractId}/text")
    public ResponseEntity<ApiResponse<Void>> deleteContractText(
            @PathVariable UUID contractId,
            @RequestHeader("X-User-Id") String userId) {
        contractService.deleteContractText(contractId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Contract text deleted for privacy."));
    }
}
