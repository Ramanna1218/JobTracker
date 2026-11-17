package com.jobtracker.service;

import com.jobtracker.dto.JobPosting;
import com.jobtracker.model.JobMatch;
import com.jobtracker.model.User;
import com.jobtracker.repository.JobMatchRepository;
import com.jobtracker.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs once every 24 hours (also triggerable manually via /api/run-now).
 * For each user with alerts enabled: searches their configured keywords,
 * skips jobs already seen, tailors the resume against new ones, saves
 * results, and emails a digest.
 */
@Service
@Slf4j
public class DailyJobRunner {

    private final UserRepository userRepository;
    private final JobMatchRepository jobMatchRepository;
    private final JobSearchService jobSearchService;
    private final ResumeTailorService resumeTailorService;
    private final EmailService emailService;

    public DailyJobRunner(UserRepository userRepository,
                           JobMatchRepository jobMatchRepository,
                           JobSearchService jobSearchService,
                           ResumeTailorService resumeTailorService,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.jobMatchRepository = jobMatchRepository;
        this.jobSearchService = jobSearchService;
        this.resumeTailorService = resumeTailorService;
        this.emailService = emailService;
    }

    // Runs every 24 hours. Adjust cron for your preferred time of day, e.g. 7am daily: "0 0 7 * * *"
    @Scheduled(cron = "${jobtracker.schedule.cron:0 0 7 * * *}")
    public void runForAllUsers() {
        List<User> users = userRepository.findAll();
        log.info("Starting daily job run for {} users", users.size());
        for (User user : users) {
            if (!user.isAlertsEnabled()) continue;
            try {
                runForUser(user);
            } catch (Exception e) {
                log.error("Daily run failed for user {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    public void runForUser(User user) {
        if (user.getJobKeywords() == null || user.getJobKeywords().isBlank()) {
            log.warn("User {} has no keywords configured, skipping", user.getEmail());
            return;
        }

        log.info("Starting run for user {} with keywords '{}'", user.getEmail(), user.getJobKeywords());
        List<JobMatch> newMatches = new ArrayList<>();

        for (String keyword : user.getJobKeywords().split(",")) {
            List<JobPosting> postings = jobSearchService.searchLast24Hours(keyword.trim(), user.getLocationPreference());

            for (JobPosting posting : postings) {
                Optional<JobMatch> existing = jobMatchRepository.findByUserAndJobId(user, posting.getJobId());
                if (existing.isPresent()) {
                    log.info("Skipping already-seen job '{}' ({})", posting.getTitle(), posting.getJobId());
                    continue;
                }

                log.info("Tailoring resume for new job '{}' @ '{}'", posting.getTitle(), posting.getCompany());
                ResumeTailorService.TailorResult tailored = resumeTailorService.tailor(user.getResumeText(), posting);

                JobMatch match = new JobMatch();
                match.setUser(user);
                match.setJobId(posting.getJobId());
                match.setTitle(clean(posting.getTitle()));
                match.setCompany(clean(posting.getCompany()));
                match.setLocation(clean(posting.getLocation()));
                match.setApplyUrl(clean(posting.getApplyUrl()));
                match.setSource(clean(posting.getSource()));
                match.setJobDescription(clean(posting.getDescription()));
                match.setMatchScore(tailored.matchScore());
                match.setTailoredBullets(clean(tailored.tailoredBullets()));
                match.setEmailed(false);

                try {
                    jobMatchRepository.save(match);
                    newMatches.add(match);
                } catch (Exception e) {
                    // One bad posting shouldn't stop the whole run
                    log.error("Could not save job '{}': {}", posting.getTitle(), e.getMessage());
                }
            }
        }

        log.info("Run complete for user {}: {} new match(es) found", user.getEmail(), newMatches.size());

        if (!newMatches.isEmpty()) {
            emailService.sendReviewNotification(user, newMatches);
            newMatches.forEach(m -> m.setEmailed(true));
            jobMatchRepository.saveAll(newMatches);
        }
    }

    // Postgres rejects the NUL character (0x00) that some job postings contain
    private static String clean(String value) {
        return value == null ? null : value.replace("\0", "");
    }
}
