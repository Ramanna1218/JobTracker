package com.jobtracker.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_matches", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "jobId"}))
@Data
public class JobMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // External job id from the source API, used to dedupe across daily runs
    @Column(nullable = false, columnDefinition = "TEXT")
    private String jobId;

    @Column(columnDefinition = "TEXT")
    private String title;
    @Column(columnDefinition = "TEXT")
    private String company;
    @Column(columnDefinition = "TEXT")
    private String location;
    @Column(columnDefinition = "TEXT")
    private String applyUrl;  // apply links from job boards are often > 255 chars
    @Column(columnDefinition = "TEXT")
    private String source; // linkedin, indeed, company_site, etc.

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    private Integer matchScore; // 0-100, from Claude tailoring step

    @Column(columnDefinition = "TEXT")
    private String tailoredBullets;

    private LocalDateTime foundAt = LocalDateTime.now();
    private boolean emailed = false;

    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.PENDING;

    public enum MatchStatus {
        PENDING,   // found, tailored, awaiting your review
        APPROVED,  // you clicked Approve - apply link was opened for you
        REJECTED   // you dismissed it, won't show again
    }
}
