package com.jobtracker.controller;

import com.jobtracker.model.JobMatch;
import com.jobtracker.repository.JobMatchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MatchController {

    private final JobMatchRepository jobMatchRepository;

    public MatchController(JobMatchRepository jobMatchRepository) {
        this.jobMatchRepository = jobMatchRepository;
    }

    // Dashboard calls this to load the review queue
    @GetMapping("/users/{userId}/matches/pending")
    public ResponseEntity<List<JobMatch>> pending(@PathVariable Long userId) {
        return ResponseEntity.ok(
                jobMatchRepository.findByUserIdAndStatusOrderByMatchScoreDesc(userId, JobMatch.MatchStatus.PENDING)
        );
    }

    // Called when you click "Approve" - just marks it, the frontend opens the apply link itself
    @PostMapping("/matches/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        JobMatch match = jobMatchRepository.findById(id).orElse(null);
        if (match == null) return ResponseEntity.notFound().build();
        match.setStatus(JobMatch.MatchStatus.APPROVED);
        jobMatchRepository.save(match);
        return ResponseEntity.ok(match);
    }

    @PostMapping("/matches/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        JobMatch match = jobMatchRepository.findById(id).orElse(null);
        if (match == null) return ResponseEntity.notFound().build();
        match.setStatus(JobMatch.MatchStatus.REJECTED);
        jobMatchRepository.save(match);
        return ResponseEntity.ok(match);
    }
}
