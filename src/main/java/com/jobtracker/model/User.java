package com.jobtracker.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    // Free-text resume content (extracted from uploaded PDF/DOCX at upload time)
    @Column(columnDefinition = "TEXT")
    private String resumeText;

    // Comma-separated search keywords, e.g. "Mechanical Design Engineer, CAD, SolidWorks"
    private String jobKeywords;

    // e.g. "Remote" or "Dayton, OH" or "Anywhere"
    private String locationPreference;

    private boolean alertsEnabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
