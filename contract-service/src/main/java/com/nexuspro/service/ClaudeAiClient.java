package com.nexuspro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for Anthropic Claude API.
 *
 * Security notes:
 * - API key injected from environment variable — never hardcoded
 * - Contract text is sent to Claude but NOT logged
 * - Response validated before parsing
 * - Rate limiting: 3 retries with exponential backoff
 * - Timeout: 60s (contracts can be long)
 */
@Service
@Slf4j
public class ClaudeAiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.model:claude-sonnet-4-20250514}")
    private String model;

    @Value("${anthropic.max-tokens:4096}")
    private int maxTokens;

    public ClaudeAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }

    /**
     * Analyse an esports contract using Claude.
     * Returns structured JSON analysis.
     */
    public String analyseContract(String contractText, String userContext) {
        String systemPrompt = buildContractAnalysisSystemPrompt();
        String userMessage  = buildContractAnalysisUserMessage(contractText, userContext);

        return callClaude(systemPrompt, userMessage);
    }

    /**
     * Generate career match scores and personalised roadmap for Discovery feature.
     */
    public String generateCareerAssessment(String answers, String profileData) {
        String systemPrompt = buildCareerAssessmentSystemPrompt();
        String userMessage  = "Player assessment answers:\n" + answers + "\n\nProfile data:\n" + profileData;

        return callClaude(systemPrompt, userMessage);
    }

    /**
     * Translate esports career achievements into professional CV language.
     */
    public String translateCareerToCV(String esportsAchievements, String targetIndustry) {
        String systemPrompt = """
            You are an expert career consultant specialising in transferable skills from esports to civilian careers.
            You translate esports achievements into professional, compelling CV language appropriate for UK employers.
            Return a JSON object with format:
            {
              "translations": [
                {
                  "original": "original esports text",
                  "translated": "professional CV version",
                  "skills": ["skill1", "skill2"],
                  "industryFit": "why this applies to target industry"
                }
              ],
              "cvSummary": "3-sentence professional summary",
              "keyStrengths": ["strength1", "strength2", "strength3"]
            }
            Return ONLY valid JSON with no additional text.
            """;

        String userMessage = "Esports achievements to translate:\n" + esportsAchievements
            + "\n\nTarget industry: " + targetIndustry;

        return callClaude(systemPrompt, userMessage);
    }

    /**
     * Generate personalised wellbeing recommendations.
     */
    public String generateWellbeingRecommendations(String wellbeingData) {
        String systemPrompt = """
            You are a sports performance psychologist specialising in esports athlete welfare.
            Analyse the provided wellbeing metrics and generate practical, evidence-based recommendations.
            Return a JSON object:
            {
              "burnoutRisk": "LOW|MEDIUM|HIGH|CRITICAL",
              "burnoutScore": 0-100,
              "recommendations": [
                {
                  "category": "sleep|training|mental|physical|nutrition",
                  "priority": "HIGH|MEDIUM|LOW",
                  "title": "short title",
                  "description": "actionable recommendation",
                  "evidenceBase": "why this works"
                }
              ],
              "summary": "overall wellness summary"
            }
            Return ONLY valid JSON. Be specific and actionable, not generic.
            """;

        return callClaude(systemPrompt, wellbeingData);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String callClaude(String systemPrompt, String userMessage) {
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "max_tokens", maxTokens,
            "system", systemPrompt,
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            )
        );

        return webClient.post()
            .uri("/v1/messages")
            .header("x-api-key", apiKey)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(response -> {
                JsonNode content = response.path("content");
                if (content.isArray() && !content.isEmpty()) {
                    return content.get(0).path("text").asText();
                }
                throw new RuntimeException("Unexpected Claude API response format");
            })
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .filter(e -> !(e instanceof WebClientResponseException.BadRequest))
                .doBeforeRetry(s -> log.warn("Retrying Claude API call, attempt {}", s.totalRetries() + 1)))
            .timeout(Duration.ofSeconds(90))
            .doOnError(e -> log.error("Claude API call failed: {}", e.getMessage()))
            .block();
    }

    private String buildContractAnalysisSystemPrompt() {
        return """
            You are a specialist UK employment and sports law paralegal assistant with expertise in esports contracts.
            
            Your role is to analyse esports contracts and identify:
            1. CRITICAL issues (must be resolved before signing — legal risk, exploitation, rights removal)
            2. WARNINGS (should be negotiated — unfair but not illegal)
            3. INFO (worth noting — standard clauses to be aware of)
            4. OK (acceptable standard clauses)
            
            Focus areas for esports contracts:
            - Image rights and likeness clauses (perpetual/unlimited use = RED FLAG)
            - Non-compete clauses (UK standard: 3-6 months; anything over 12 months likely unenforceable)
            - Revenue sharing and prize money splits (standard: 15-25% org cut)
            - Intellectual property ownership (streams, content, game accounts)
            - Termination clauses and notice periods
            - Salary compliance with UK National Living Wage
            - GDPR data handling clauses
            - Exclusivity clauses
            - Stream/content creation obligations and revenue splits
            
            Return a JSON object with this exact structure:
            {
              "summary": "2-3 sentence plain English summary",
              "riskScore": 0-100,
              "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
              "flags": [
                {
                  "clauseReference": "Clause X.X",
                  "severity": "CRITICAL|WARNING|INFO|OK",
                  "title": "short descriptive title",
                  "description": "plain English explanation of the issue",
                  "legalContext": "relevant UK law or industry standard",
                  "recommendedAction": "specific negotiation recommendation",
                  "exactQuote": "the problematic text from the contract"
                }
              ],
              "positiveFindings": ["list of acceptable/good clauses found"],
              "overallRecommendation": "DO_NOT_SIGN|NEGOTIATE|SIGN_WITH_CAVEATS|SAFE_TO_SIGN",
              "ukLawCompliance": "assessment of UK employment law compliance"
            }
            
            IMPORTANT:
            - Be specific — cite exact clause numbers where possible
            - Use plain English — this is for players, not lawyers
            - Return ONLY valid JSON with no preamble or explanation outside the JSON
            - Base all legal assessments on UK law (Employment Rights Act 1996, etc.)
            """;
    }

    private String buildContractAnalysisUserMessage(String contractText, String context) {
        return "Player context: " + (context != null ? context : "Esports player, UK based")
            + "\n\n--- CONTRACT TEXT ---\n" + contractText + "\n--- END CONTRACT ---";
    }

    private String buildCareerAssessmentSystemPrompt() {
        return """
            You are a specialist esports career advisor with deep knowledge of the UK esports industry.
            
            Based on a player's assessment answers, generate personalised career pathway matches.
            
            Available career paths in esports:
            - Professional Player (competitive play)
            - Head Coach / Assistant Coach
            - Performance Analyst / Data Analyst
            - Team Manager / General Manager
            - Caster / Broadcaster / Analyst (desk)
            - Event Manager / Tournament Organiser
            - Esports Journalist / Content Creator
            - Brand Manager / Partnerships
            - Esports Psychologist / Wellbeing Coach
            - Second career: Software Developer, Data Scientist, Project Manager, Finance
            
            Return JSON:
            {
              "topMatch": {
                "career": "career title",
                "matchScore": 0-100,
                "reasons": ["why this matches"],
                "nextSteps": ["immediate actions to take"]
              },
              "allMatches": [
                {
                  "career": "title",
                  "matchScore": 0-100,
                  "fit": "one line explanation"
                }
              ],
              "roadmap": [
                {
                  "phase": "timeframe e.g. Month 1-3",
                  "action": "specific action",
                  "resource": "recommended resource or platform"
                }
              ],
              "personalityInsights": "2-3 sentences on their strengths"
            }
            Return ONLY valid JSON.
            """;
    }
}
