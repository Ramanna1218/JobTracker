package com.jobtracker.repository;

import com.jobtracker.model.JobMatch;
import com.jobtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {
    Optional<JobMatch> findByUserAndJobId(User user, String jobId);
    List<JobMatch> findByUserAndEmailedFalse(User user);
    List<JobMatch> findByUserIdAndStatusOrderByMatchScoreDesc(Long userId, JobMatch.MatchStatus status);
}
