package com.jobtracker.dto;

import lombok.Data;

@Data
public class JobPosting {
    private String jobId;
    private String title;
    private String company;
    private String location;
    private String description;
    private String applyUrl;
    private String source;
    private String postedAt; // ISO string, used for the last-24h filter
}
