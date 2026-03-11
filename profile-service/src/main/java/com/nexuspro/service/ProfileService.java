package com.nexuspro.service;

import com.nexuspro.exception.BusinessException;
import com.nexuspro.exception.ResourceNotFoundException;
import com.nexuspro.model.entity.CareerProfile;
import com.nexuspro.model.entity.Certification;
import com.nexuspro.model.entity.Tournament;
import com.nexuspro.repository.CertificationRepository;
import com.nexuspro.repository.CareerProfileRepository;
import com.nexuspro.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Career Passport service.
 * Manages professional profiles, tournament history, certifications, and career scoring.
 *
 * Career Score formula (max 1000):
 *   - Profile completeness  : up to 200 pts
 *   - Tournament history    : up to 300 pts (placement-weighted)
 *   - Certifications        : up to 200 pts
 *   - Years active          : up to 100 pts
 *   - Skill diversity       : up to 100 pts
 *   - Wellbeing streak      : up to 100 pts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final CareerProfileRepository profileRepo;
    private final TournamentRepository    tournamentRepo;
    private final CertificationRepository certRepo;
    private final KafkaTemplate<String, String> kafka;

    // ── PROFILE ────────────────────────────────────────────────────────────

    @Transactional
    public CareerProfile createProfile(UUID userId, String username, String displayName) {
        if (profileRepo.existsByUserId(userId))
            throw new BusinessException("Profile already exists", "PROFILE_EXISTS");

        CareerProfile profile = CareerProfile.builder()
            .userId(userId)
            .username(username)
            .displayName(displayName)
            .completionPct(15)  // Base for account creation
            .build();

        return profileRepo.save(profile);
    }

    public CareerProfile getProfileByUserId(UUID userId) {
        return profileRepo.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile", userId.toString()));
    }

    public CareerProfile getProfileByUsername(String username) {
        return profileRepo.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("Profile", username));
    }

    @Transactional
    public CareerProfile updateProfile(UUID userId, CareerProfile updates) {
        CareerProfile profile = getProfileByUserId(userId);

        if (updates.getBio() != null)
            profile.setBio(sanitise(updates.getBio().substring(0, Math.min(updates.getBio().length(), 1000))));
        if (updates.getPrimaryGame() != null)   profile.setPrimaryGame(sanitise(updates.getPrimaryGame()));
        if (updates.getPrimaryRole() != null)   profile.setPrimaryRole(sanitise(updates.getPrimaryRole()));
        if (updates.getCurrentTeam() != null)   profile.setCurrentTeam(sanitise(updates.getCurrentTeam()));
        if (updates.getCurrentTier() != null)   profile.setCurrentTier(sanitise(updates.getCurrentTier()));
        if (updates.getCountryCode() != null)   profile.setCountryCode(updates.getCountryCode());
        if (updates.getTwitterUrl() != null)    profile.setTwitterUrl(validateUrl(updates.getTwitterUrl()));
        if (updates.getTwitchUrl() != null)     profile.setTwitchUrl(validateUrl(updates.getTwitchUrl()));
        if (updates.getYoutubeUrl() != null)    profile.setYoutubeUrl(validateUrl(updates.getYoutubeUrl()));
        if (updates.getLinkedinUrl() != null)   profile.setLinkedinUrl(validateUrl(updates.getLinkedinUrl()));
        if (updates.getVisibility() != null)    profile.setVisibility(updates.getVisibility());
        if (updates.getSkills() != null)        profile.setSkills(validateSkills(updates.getSkills()));

        // Recalculate completion and career score
        int completion = calculateCompletion(profile);
        profile.setCompletionPct(completion);

        int score = calculateCareerScore(profile, userId);
        profile.setCareerScore(score);

        return profileRepo.save(profile);
    }

    // ── TOURNAMENTS ────────────────────────────────────────────────────────

    @Transactional
    public Tournament addTournament(UUID userId, Tournament tournament) {
        CareerProfile profile = getProfileByUserId(userId);

        tournament.setId(null);
        tournament.setProfileId(profile.getId());
        tournament.setVerified(false);  // Requires manual verification
        tournament.setCareerPoints(calculateTournamentPoints(tournament));

        Tournament saved = tournamentRepo.save(tournament);

        // Recalculate career score
        recalculateCareerScore(userId);

        // Request verification if proof URL provided
        if (saved.getProofUrl() != null && !saved.getProofUrl().isBlank()) {
            kafka.send("profile.tournament.verify_requested",
                "{\"tournamentId\":\"" + saved.getId() + "\",\"userId\":\"" + userId + "\"}");
        }

        return saved;
    }

    public Page<Tournament> getTournaments(UUID userId, Pageable pageable) {
        CareerProfile profile = getProfileByUserId(userId);
        return tournamentRepo.findByProfileIdOrderByEventDateDesc(profile.getId(), pageable);
    }

    @Transactional
    public void deleteTournament(UUID tournamentId, UUID userId) {
        CareerProfile profile = getProfileByUserId(userId);
        Tournament t = tournamentRepo.findById(tournamentId)
            .orElseThrow(() -> new ResourceNotFoundException("Tournament", tournamentId.toString()));
        if (!t.getProfileId().equals(profile.getId()))
            throw new BusinessException("Not authorised", "FORBIDDEN");
        tournamentRepo.delete(t);
        recalculateCareerScore(userId);
    }

    // ── CERTIFICATIONS ─────────────────────────────────────────────────────

    @Transactional
    public Certification addCertification(UUID userId, Certification cert) {
        CareerProfile profile = getProfileByUserId(userId);
        cert.setId(null);
        cert.setProfileId(profile.getId());
        cert.setVerified(false);

        Certification saved = certRepo.save(cert);
        recalculateCareerScore(userId);
        return saved;
    }

    public Page<Certification> getCertifications(UUID userId, Pageable pageable) {
        CareerProfile profile = getProfileByUserId(userId);
        return certRepo.findByProfileIdOrderByIssueDateDesc(profile.getId(), pageable);
    }

    @Transactional
    public void deleteCertification(UUID certId, UUID userId) {
        CareerProfile profile = getProfileByUserId(userId);
        Certification c = certRepo.findById(certId)
            .orElseThrow(() -> new ResourceNotFoundException("Certification", certId.toString()));
        if (!c.getProfileId().equals(profile.getId()))
            throw new BusinessException("Not authorised", "FORBIDDEN");
        certRepo.delete(c);
        recalculateCareerScore(userId);
    }

    // ── CAREER SCORING ─────────────────────────────────────────────────────

    @Transactional
    public void recalculateCareerScore(UUID userId) {
        CareerProfile profile = getProfileByUserId(userId);
        int score = calculateCareerScore(profile, userId);
        profile.setCareerScore(score);
        profileRepo.save(profile);
    }

    private int calculateCareerScore(CareerProfile profile, UUID userId) {
        int score = 0;

        // Completeness (max 200)
        score += (int) (profile.getCompletionPct() * 2.0);

        // Tournaments (max 300)
        long tournamentCount = tournamentRepo.countByProfileId(profile.getId());
        long verifiedCount   = tournamentRepo.countByProfileIdAndVerified(profile.getId(), true);
        int  topPlacements   = tournamentRepo.countTop3Placements(profile.getId());
        score += Math.min(100, tournamentCount * 5);
        score += Math.min(100, verifiedCount * 10);
        score += Math.min(100, topPlacements * 20);

        // Certifications (max 200)
        long certCount = certRepo.countByProfileId(profile.getId());
        score += Math.min(200, certCount * 40);

        // Skill diversity (max 100) — if skills map has 6+ entries
        if (profile.getSkills() != null && profile.getSkills().size() >= 6) score += 100;
        else if (profile.getSkills() != null) score += profile.getSkills().size() * 15;

        // Passport verified bonus
        if (profile.isPassportVerified()) score += 50;

        return Math.min(1000, score);
    }

    private int calculateCompletion(CareerProfile p) {
        int pts = 15; // base
        if (p.getBio()         != null && !p.getBio().isBlank())         pts += 15;
        if (p.getPrimaryGame() != null && !p.getPrimaryGame().isBlank()) pts += 10;
        if (p.getPrimaryRole() != null && !p.getPrimaryRole().isBlank()) pts += 10;
        if (p.getAvatarUrl()   != null && !p.getAvatarUrl().isBlank())   pts += 10;
        if (p.getCountryCode() != null)                                  pts += 5;
        if (p.getSkills()      != null && !p.getSkills().isEmpty())      pts += 15;
        if (p.getTwitterUrl()  != null || p.getTwitchUrl() != null)      pts += 10;
        if (p.getLinkedinUrl() != null)                                  pts += 10;
        return Math.min(100, pts);
    }

    private int calculateTournamentPoints(Tournament t) {
        String placement = t.getPlacement().toLowerCase();
        int base = 50;
        if (placement.contains("1st"))       base = 500;
        else if (placement.contains("2nd"))  base = 350;
        else if (placement.contains("3rd"))  base = 250;
        else if (placement.contains("top 4")) base = 150;
        else if (placement.contains("top 8")) base = 100;
        else if (placement.contains("top 16")) base = 75;

        // Bonus for verified results
        if (t.isVerified()) base = (int) (base * 1.2);
        return base;
    }

    private String sanitise(String input) {
        return input == null ? null : input.replaceAll("<[^>]*>", "").trim();
    }

    private String validateUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (!url.startsWith("https://") && !url.startsWith("http://"))
            throw new BusinessException("Invalid URL: " + url, "INVALID_URL");
        return url.substring(0, Math.min(url.length(), 255));
    }

    private Map<String, Integer> validateSkills(Map<String, Integer> skills) {
        skills.forEach((k, v) -> {
            if (v < 0 || v > 100)
                throw new BusinessException("Skill score must be 0–100 for: " + k, "INVALID_SKILL_SCORE");
        });
        return skills;
    }
}
