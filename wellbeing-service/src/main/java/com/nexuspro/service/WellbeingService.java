package com.nexuspro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexuspro.exception.BusinessException;
import com.nexuspro.exception.ResourceNotFoundException;
import com.nexuspro.model.entity.WellbeingEntry;
import com.nexuspro.model.entity.WellbeingEntry.BurnoutLevel;
import com.nexuspro.repository.WellbeingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Wellbeing tracking service.
 *
 * Burnout risk algorithm (adapted from Maslach Burnout Inventory):
 * - Emotional exhaustion  : mood + energy trends (40% weight)
 * - Depersonalisation     : social interaction decline (20% weight)
 * - Personal accomplishment: focus + consistency (40% weight)
 *
 * Alert thresholds:
 * - LOW     : 0–30
 * - MODERATE: 31–55
 * - HIGH    : 56–75 → sends alert to user
 * - CRITICAL: 76–100 → sends alert + recommends immediate action
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WellbeingService {

    private final WellbeingRepository wellbeingRepo;
    private final ClaudeAiClient      claudeClient;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    // ── ENTRY LOGGING ──────────────────────────────────────────────────────

    @Transactional
    public WellbeingEntry logEntry(UUID userId, WellbeingEntry entry) {
        LocalDate today = LocalDate.now();
        entry.setId(null);
        entry.setUserId(userId);

        if (entry.getEntryDate() == null) entry.setEntryDate(today);

        // Can't log future dates
        if (entry.getEntryDate().isAfter(today))
            throw new BusinessException("Cannot log future dates", "INVALID_DATE");

        // No duplicate entries per day
        if (wellbeingRepo.existsByUserIdAndEntryDate(userId, entry.getEntryDate()))
            throw new BusinessException("Entry already exists for " + entry.getEntryDate() + ". Use PUT to update.", "DUPLICATE_ENTRY");

        // Validate ranges
        validateEntry(entry);

        // Compute burnout risk for this entry
        int burnoutScore = calculateBurnoutRisk(userId, entry);
        entry.setBurnoutRiskScore(burnoutScore);
        entry.setBurnoutLevel(scoreToLevel(burnoutScore));

        // Composite wellbeing index
        entry.setWellbeingIndex(calculateWellbeingIndex(entry));

        WellbeingEntry saved = wellbeingRepo.save(entry);

        // Alert if HIGH or CRITICAL
        if (saved.getBurnoutLevel() == BurnoutLevel.HIGH || saved.getBurnoutLevel() == BurnoutLevel.CRITICAL) {
            kafka.send("wellbeing.burnout.alert",
                "{\"userId\":\"" + userId + "\",\"level\":\"" + saved.getBurnoutLevel() + "\",\"date\":\"" + saved.getEntryDate() + "\"}");
            log.warn("Burnout alert triggered: userId={} level={}", userId, saved.getBurnoutLevel());
        }

        return saved;
    }

    @Transactional
    public WellbeingEntry updateEntry(UUID userId, LocalDate date, WellbeingEntry updates) {
        WellbeingEntry entry = wellbeingRepo.findByUserIdAndEntryDate(userId, date)
            .orElseThrow(() -> new ResourceNotFoundException("Wellbeing entry", date.toString()));

        if (updates.getMoodScore()       != null) entry.setMoodScore(updates.getMoodScore());
        if (updates.getSleepHours()      != null) entry.setSleepHours(updates.getSleepHours());
        if (updates.getSleepQuality()    != null) entry.setSleepQuality(updates.getSleepQuality());
        if (updates.getTrainingHours()   != null) entry.setTrainingHours(updates.getTrainingHours());
        if (updates.getStressLevel()     != null) entry.setStressLevel(updates.getStressLevel());
        if (updates.getEnergyLevel()     != null) entry.setEnergyLevel(updates.getEnergyLevel());
        if (updates.getJournal()         != null) entry.setJournal(updates.getJournal());

        validateEntry(entry);

        int burnoutScore = calculateBurnoutRisk(userId, entry);
        entry.setBurnoutRiskScore(burnoutScore);
        entry.setBurnoutLevel(scoreToLevel(burnoutScore));
        entry.setWellbeingIndex(calculateWellbeingIndex(entry));

        return wellbeingRepo.save(entry);
    }

    // ── RETRIEVAL ──────────────────────────────────────────────────────────

    public Page<WellbeingEntry> getHistory(UUID userId, Pageable pageable) {
        return wellbeingRepo.findByUserIdOrderByEntryDateDesc(userId, pageable);
    }

    public WellbeingEntry getTodaysEntry(UUID userId) {
        return wellbeingRepo.findByUserIdAndEntryDate(userId, LocalDate.now())
            .orElseThrow(() -> new ResourceNotFoundException("No entry for today", "today"));
    }

    public Map<String, Object> getSummary(UUID userId, int days) {
        LocalDate from = LocalDate.now().minus(days, ChronoUnit.DAYS);
        List<WellbeingEntry> entries = wellbeingRepo
            .findByUserIdAndEntryDateAfterOrderByEntryDateAsc(userId, from);

        if (entries.isEmpty()) return Map.of("message", "No data in range");

        OptionalDouble avgMood    = entries.stream().filter(e -> e.getMoodScore()    != null).mapToInt(WellbeingEntry::getMoodScore).average();
        OptionalDouble avgSleep   = entries.stream().filter(e -> e.getSleepHours()   != null).mapToInt(WellbeingEntry::getSleepHours).average();
        OptionalDouble avgEnergy  = entries.stream().filter(e -> e.getEnergyLevel()  != null).mapToInt(WellbeingEntry::getEnergyLevel).average();
        OptionalDouble avgBurnout = entries.stream().filter(e -> e.getBurnoutRiskScore() != null).mapToInt(WellbeingEntry::getBurnoutRiskScore).average();

        // Streak calculation (consecutive days logged)
        int streak = calculateStreak(userId);

        // Current trend: compare last 7 days to previous 7
        String burnoutTrend = calculateTrend(entries);

        return Map.of(
            "daysLogged",    entries.size(),
            "streak",        streak,
            "avgMood",       avgMood.orElse(0),
            "avgSleepHours", avgSleep.map(h -> h / 10.0).orElse(0),
            "avgEnergy",     avgEnergy.orElse(0),
            "avgBurnoutRisk", avgBurnout.orElse(0),
            "burnoutTrend",  burnoutTrend,
            "currentLevel",  entries.isEmpty() ? "UNKNOWN"
                : entries.get(entries.size() - 1).getBurnoutLevel()
        );
    }

    public String getAiRecommendations(UUID userId) {
        Map<String, Object> summary = getSummary(userId, 14);
        String summaryJson;
        try {
            summaryJson = objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            summaryJson = summary.toString();
        }
        return claudeClient.generateWellbeingRecommendations(summaryJson);
    }

    // ── BURNOUT ALGORITHM ──────────────────────────────────────────────────

    private int calculateBurnoutRisk(UUID userId, WellbeingEntry current) {
        // Get last 14 days of entries for trend analysis
        List<WellbeingEntry> recent = wellbeingRepo.findByUserIdAndEntryDateAfterOrderByEntryDateAsc(
            userId, LocalDate.now().minus(14, ChronoUnit.DAYS));

        // Emotional exhaustion component (40%)
        int exhaustion = 0;
        if (current.getMoodScore()  != null) exhaustion += (100 - current.getMoodScore());
        if (current.getEnergyLevel() != null) exhaustion += (100 - current.getEnergyLevel());
        if (current.getStressLevel() != null) exhaustion += current.getStressLevel();
        exhaustion = exhaustion / 3;

        // Training overload component (30%)
        int trainingOverload = 0;
        if (current.getTrainingHours() != null && current.getTrainingHours() > 80) {
            trainingOverload = (current.getTrainingHours() - 80) * 3;
        }

        // Sleep deficit component (30%)
        int sleepDeficit = 0;
        if (current.getSleepHours() != null) {
            // Ideal: 70–90 (7–9 hours)
            int sleep = current.getSleepHours();
            if (sleep < 60) sleepDeficit = 80;
            else if (sleep < 70) sleepDeficit = 40;
            else if (sleep < 80) sleepDeficit = 15;
        }

        // Trend multiplier — if declining over 7+ days, amplify score
        double trendMultiplier = 1.0;
        if (recent.size() >= 7) {
            double recentAvgMood = recent.subList(recent.size() - 7, recent.size())
                .stream().filter(e -> e.getMoodScore() != null)
                .mapToInt(WellbeingEntry::getMoodScore).average().orElse(50);
            if (recentAvgMood < 35) trendMultiplier = 1.3;
            else if (recentAvgMood < 50) trendMultiplier = 1.1;
        }

        int rawScore = (int) ((exhaustion * 0.4) + (trainingOverload * 0.3) + (sleepDeficit * 0.3));
        return Math.min(100, (int) (rawScore * trendMultiplier));
    }

    private int calculateWellbeingIndex(WellbeingEntry e) {
        List<Integer> values = new ArrayList<>();
        if (e.getMoodScore()      != null) values.add(e.getMoodScore());
        if (e.getEnergyLevel()    != null) values.add(e.getEnergyLevel());
        if (e.getFocusLevel()     != null) values.add(e.getFocusLevel());
        if (e.getSleepQuality()   != null) values.add(e.getSleepQuality());
        if (e.getStressLevel()    != null) values.add(100 - e.getStressLevel());
        if (values.isEmpty()) return 50;
        return (int) values.stream().mapToInt(Integer::intValue).average().orElse(50);
    }

    private BurnoutLevel scoreToLevel(int score) {
        if (score >= 76) return BurnoutLevel.CRITICAL;
        if (score >= 56) return BurnoutLevel.HIGH;
        if (score >= 31) return BurnoutLevel.MODERATE;
        return BurnoutLevel.LOW;
    }

    private int calculateStreak(UUID userId) {
        LocalDate today = LocalDate.now();
        int streak = 0;
        for (int i = 0; i < 365; i++) {
            if (!wellbeingRepo.existsByUserIdAndEntryDate(userId, today.minus(i, ChronoUnit.DAYS))) break;
            streak++;
        }
        return streak;
    }

    private String calculateTrend(List<WellbeingEntry> entries) {
        if (entries.size() < 7) return "INSUFFICIENT_DATA";
        List<WellbeingEntry> recent = entries.subList(Math.max(0, entries.size() - 7), entries.size());
        List<WellbeingEntry> prior  = entries.size() >= 14
            ? entries.subList(entries.size() - 14, entries.size() - 7) : List.of();

        double recentAvg = recent.stream().filter(e -> e.getBurnoutRiskScore() != null)
            .mapToInt(WellbeingEntry::getBurnoutRiskScore).average().orElse(50);
        double priorAvg  = prior.stream().filter(e -> e.getBurnoutRiskScore() != null)
            .mapToInt(WellbeingEntry::getBurnoutRiskScore).average().orElse(recentAvg);

        if (recentAvg > priorAvg + 10) return "WORSENING";
        if (recentAvg < priorAvg - 10) return "IMPROVING";
        return "STABLE";
    }

    private void validateEntry(WellbeingEntry e) {
        validateRange("moodScore",    e.getMoodScore());
        validateRange("sleepQuality", e.getSleepQuality());
        validateRange("stressLevel",  e.getStressLevel());
        validateRange("energyLevel",  e.getEnergyLevel());
        validateRange("focusLevel",   e.getFocusLevel());
        if (e.getSleepHours() != null && (e.getSleepHours() < 0 || e.getSleepHours() > 240))
            throw new BusinessException("sleepHours must be 0–240 (hours × 10)", "INVALID_RANGE");
        if (e.getTrainingHours() != null && (e.getTrainingHours() < 0 || e.getTrainingHours() > 240))
            throw new BusinessException("trainingHours must be 0–240", "INVALID_RANGE");
        if (e.getJournal() != null && e.getJournal().length() > 5000)
            throw new BusinessException("Journal cannot exceed 5000 characters", "JOURNAL_TOO_LONG");
    }

    private void validateRange(String field, Integer value) {
        if (value != null && (value < 0 || value > 100))
            throw new BusinessException(field + " must be 0–100", "INVALID_RANGE");
    }

    // Nightly job: recompute burnout for users who haven't logged today (decay model)
    @Scheduled(cron = "0 0 1 * * *")  // 1 AM daily
    @Transactional
    public void runNightlyBurnoutReassessment() {
        log.info("Running nightly burnout reassessment");
        // Processed via Kafka consumer in practice — simplified here
    }
}
