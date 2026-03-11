package com.nexuspro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexuspro.exception.BusinessException;
import com.nexuspro.exception.ResourceNotFoundException;
import com.nexuspro.model.entity.Contract;
import com.nexuspro.model.entity.Contract.*;
import com.nexuspro.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contract analysis pipeline:
 * 1. Upload → validate file type and size
 * 2. Extract text from PDF/DOCX
 * 3. Strip PII before sending to AI (optional based on config)
 * 4. Call Claude API asynchronously
 * 5. Parse and persist results
 * 6. Notify user via Kafka
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {

    private final ContractRepository contractRepo;
    private final ClaudeAiClient claudeClient;
    private final ObjectMapper objectMapper;

    @Value("${nexuspro.contract.max-file-size-mb:10}")
    private int maxFileSizeMb;

    @Value("${nexuspro.contract.max-text-chars:150000}")
    private int maxTextChars;

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    // ── UPLOAD ─────────────────────────────────────────────────────────────

    @Transactional
    public Contract uploadAndAnalyse(MultipartFile file, String userId, String userContext) {
        // Security: validate file
        validateFile(file);

        // Extract text
        String text = extractText(file);

        if (text.isBlank() || text.length() < 100) {
            throw new BusinessException("Could not extract meaningful text from the document. "
                + "Please ensure it is not a scanned image PDF.", "EXTRACTION_FAILED");
        }

        // Truncate if too long
        if (text.length() > maxTextChars) {
            text = text.substring(0, maxTextChars);
            log.info("Contract text truncated to {} chars for userId={}", maxTextChars, userId);
        }

        // Determine file type
        String fileType = file.getContentType().contains("pdf") ? "PDF" : "DOCX";

        // Save initial record
        Contract contract = Contract.builder()
            .userId(UUID.fromString(userId))
            .fileName(sanitiseFileName(file.getOriginalFilename()))
            .fileType(fileType)
            .extractedText(text)
            .status(AnalysisStatus.PENDING)
            .build();

        contract = contractRepo.save(contract);

        // Analyse asynchronously
        analyseAsync(contract.getId(), text, userContext);

        return contract;
    }

    @Async("contractAnalysisExecutor")
    @Transactional
    public void analyseAsync(UUID contractId, String text, String userContext) {
        Contract contract = contractRepo.findById(contractId).orElseThrow();

        try {
            contract.setStatus(AnalysisStatus.PROCESSING);
            contractRepo.save(contract);

            // Call Claude
            String rawResponse = claudeClient.analyseContract(text, userContext);

            // Parse response
            ContractAnalysis analysis = objectMapper.readValue(rawResponse, ContractAnalysis.class);

            // Calculate stats
            int flagged = analysis.getFlags() != null
                ? (int) analysis.getFlags().stream()
                    .filter(f -> !f.getSeverity().equals("OK"))
                    .count()
                : 0;

            // Determine risk score from AI response
            int riskScore = extractRiskScore(rawResponse);

            contract.setAnalysis(analysis);
            contract.setStatus(AnalysisStatus.COMPLETED);
            contract.setAnalysedAt(Instant.now());
            contract.setRiskScore(riskScore);
            contract.setRiskLevel(scoreToLevel(riskScore));
            contract.setTotalClauses(analysis.getFlags() != null ? analysis.getFlags().size() : 0);
            contract.setFlaggedClauses(flagged);
            contractRepo.save(contract);

            log.info("Contract analysis completed: contractId={} riskScore={}", contractId, riskScore);

        } catch (Exception e) {
            log.error("Contract analysis failed for contractId={}: {}", contractId, e.getMessage(), e);
            contract.setStatus(AnalysisStatus.FAILED);
            contractRepo.save(contract);
        }
    }

    public Contract getContract(UUID contractId, String userId) {
        return contractRepo.findByIdAndUserId(contractId, UUID.fromString(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Contract", contractId.toString()));
    }

    public Page<Contract> getUserContracts(String userId, Pageable pageable) {
        return contractRepo.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId), pageable);
    }

    @Transactional
    public void deleteContractText(UUID contractId, String userId) {
        Contract contract = getContract(contractId, userId);
        // GDPR: delete raw text, keep analysis
        contract.setExtractedText(null);
        contract.setTextDeletedAt(Instant.now());
        contractRepo.save(contract);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file.isEmpty())
            throw new BusinessException("File is empty", "EMPTY_FILE");

        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes)
            throw new BusinessException("File exceeds maximum size of " + maxFileSizeMb + "MB", "FILE_TOO_LARGE");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType))
            throw new BusinessException("Only PDF and DOCX files are accepted", "INVALID_FILE_TYPE");

        // Magic byte validation — don't trust Content-Type header alone
        try {
            byte[] header = new byte[8];
            int read = file.getInputStream().read(header);
            if (!isValidFileHeader(header, contentType))
                throw new BusinessException("File content does not match declared type", "INVALID_FILE_CONTENT");
        } catch (IOException e) {
            throw new BusinessException("Could not read file", "FILE_READ_ERROR");
        }
    }

    private boolean isValidFileHeader(byte[] header, String contentType) {
        if (contentType.contains("pdf")) {
            // PDF magic bytes: %PDF
            return header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46;
        }
        if (contentType.contains("wordprocessingml")) {
            // DOCX is a ZIP: PK header
            return header[0] == 0x50 && header[1] == 0x4B;
        }
        return false;
    }

    private String extractText(MultipartFile file) {
        try {
            if (file.getContentType().contains("pdf")) {
                try (PDDocument doc = PDDocument.load(file.getInputStream())) {
                    if (doc.isEncrypted())
                        throw new BusinessException("Encrypted PDFs are not supported", "ENCRYPTED_PDF");
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(doc);
                }
            } else {
                try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                    return new XWPFWordExtractor(doc).getText();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Text extraction failed: {}", e.getMessage());
            throw new BusinessException("Failed to extract text from document", "EXTRACTION_FAILED");
        }
    }

    private String sanitiseFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(name.length(), 255));
    }

    private int extractRiskScore(String jsonResponse) {
        try {
            return objectMapper.readTree(jsonResponse).path("riskScore").asInt(50);
        } catch (Exception e) {
            return 50;
        }
    }

    private RiskLevel scoreToLevel(int score) {
        if (score >= 75) return RiskLevel.CRITICAL;
        if (score >= 50) return RiskLevel.HIGH;
        if (score >= 25) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}
