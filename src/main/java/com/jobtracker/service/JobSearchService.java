package com.jobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.JobPosting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JobSearchService {

    private final RestTemplate restTemplate;

    @Value("${jsearch.api.key}")
    private String apiKey;

    @Value("${jsearch.api.host:jsearch.p.rapidapi.com}")
    private String apiHost;

    public JobSearchService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<JobPosting> searchLast24Hours(String keywords, String location) {
        URI uri = UriComponentsBuilder.fromHttpUrl("https://jsearch.p.rapidapi.com/search-v2")
                .queryParam("query", keywords + (location != null && !location.isBlank() ? " in " + location : ""))
                .queryParam("date_posted", "today")
                .queryParam("num_pages", "1")
                .queryParam("country", "us")
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key", apiKey);
        headers.set("X-RapidAPI-Host", apiHost);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        List<JobPosting> results = new ArrayList<>();
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        try {
            log.info("Calling JSearch URL: {}", uri);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode data = root.path("data").path("jobs");

            int skippedOld = 0;
            for (JsonNode job : data) {
                String postedAtStr = job.path("job_posted_at_datetime_utc").asText(null);
                if (postedAtStr != null) {
                    try {
                        Instant postedAt = Instant.parse(postedAtStr);
                        if (postedAt.isBefore(cutoff)) {
                            skippedOld++;
                            continue; // older than 24h - JSearch's own filter isn't reliable, so enforce it ourselves
                        }
                    } catch (Exception parseEx) {
                        log.warn("Could not parse job_posted_at_datetime_utc '{}', keeping job anyway", postedAtStr);
                    }
                }

                JobPosting posting = new JobPosting();
                posting.setJobId(job.path("job_id").asText());
                posting.setTitle(job.path("job_title").asText());
                posting.setCompany(job.path("employer_name").asText());
                String city = job.path("job_city").asText("");
                String state = job.path("job_state").asText("");
                posting.setLocation((city + " " + state).trim());
                posting.setDescription(job.path("job_description").asText());
                posting.setApplyUrl(job.path("job_apply_link").asText());
                posting.setSource(job.path("job_publisher").asText("Unknown"));
                posting.setPostedAt(postedAtStr);
                results.add(posting);
            }
            log.info("Filtered out {} posting(s) older than 24h", skippedOld);
        } catch (Exception e) {
            log.error("JSearch API call failed for keywords='{}' location='{}': {}", keywords, location, e.getMessage());
        }

        log.info("JSearch returned {} posting(s) within last 24h for keywords='{}' location='{}'", results.size(), keywords, location);
        return results;
    }
}
