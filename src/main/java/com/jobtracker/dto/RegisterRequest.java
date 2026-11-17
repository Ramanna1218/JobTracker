package com.jobtracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String fullName;

    @NotBlank
    private String resumeText;

    @NotBlank
    private String jobKeywords; // comma separated, e.g. "Business Analyst, Data Analyst"

    private String locationPreference;
}
