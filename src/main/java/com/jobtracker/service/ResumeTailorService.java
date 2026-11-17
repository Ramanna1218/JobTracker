package com.jobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.JobPosting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Calls the Anthropic Messages API to score a resume against a job
 * description and produce 3-5 tailored resume bullets. Field-agnostic:
 * works the same for a software resume or a mechanical-engineering resume,
 * since it's just text-in/text-out.
 */
@Service
@Slf4j
public class ResumeTailorService {

    private final RestTemplate restTemplate;

    @Value("${anthropic.api.key}")
    private String apiKey;

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    @Value("${anthropic.model:claude-haiku-5-5}")
    private String model;

    public ResumeTailorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record TailorResult(int matchScore, String tailoredBullets) {}

    public TailorResult tailor(String resumeText, JobPosting job) {
        String prompt = """
                You are a resume-tailoring assistant. Given a candidate's resume and a job
                description, do two things:
                1. Score how well the resume matches the job on a 0-100 scale, based on
                   skills, experience level, and domain overlap.
                2. Write 3-5 tailored resume bullet points (using the candidate's REAL
                   experience only - never invent skills or projects they don't have)
                   that could be added or swapped in to better match this specific job.

                Respond ONLY with valid JSON, no markdown fences, in this exact shape:
                {"matchScore": <int>, "bullets": ["...", "..."]}

                RESUME:
                %s

                JOB TITLE: %s
                COMPANY: %s
                JOB DESCRIPTION:
                %s
                """.formatted(resumeText, job.getTitle(), job.getCompany(),
                        job.getDescription() == null ? "" : job.getDescription());

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", 2048);
        body.put("messages", new Object[]{
                Map.of("role", "user", "content", prompt)
        });

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            // Collect every text block (the reply can contain non-text blocks too)
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText());
                }
            }
            String text = sb.toString();
            if (text.isBlank()) {
                String raw = response.getBody() == null ? "" : response.getBody();
                throw new IllegalStateException("Empty reply, stop_reason="
                        + root.path("stop_reason").asText() + ", body="
                        + raw.substring(0, Math.min(raw.length(), 400)));
            }

            // Keep only the JSON object, even if the model adds text or fences around it
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalStateException("No JSON in model reply: " + text);
            }
            text = text.substring(start, end + 1);

            JsonNode parsed = mapper.readTree(text);
            JsonNode scoreNode = findField(parsed, "matchScore", "match_score", "score");
            JsonNode bulletNode = findField(parsed, "bullets", "tailoredBullets", "tailored_bullets");
            if (scoreNode == null) {
                throw new IllegalStateException("No score in model reply: "
                        + text.substring(0, Math.min(text.length(), 400)));
            }
            int score = scoreNode.asInt(0);
            StringBuilder bullets = new StringBuilder();
            if (bulletNode != null) {
                for (JsonNode b : bulletNode) {
                    bullets.append("- ").append(b.asText()).append("\n");
                }
            }
            return new TailorResult(score, bullets.toString());
        } catch (HttpStatusCodeException e) {
            log.error("Resume tailoring failed for job '{}': HTTP {} - {}",
                    job.getTitle(), e.getStatusCode(), e.getResponseBodyAsString());
            return new TailorResult(0, "Tailoring unavailable for this job.");
        } catch (Exception e) {
            log.error("Resume tailoring failed for job '{}': {}", job.getTitle(), e.toString());
            return new TailorResult(0, "Tailoring unavailable for this job.");
        }
    }

    // Finds the first field with one of the given names, searching nested objects too
    private static JsonNode findField(JsonNode node, String... names) {
        if (node == null || !node.isObject()) return null;
        for (String n : names) {
            if (node.has(n)) return node.get(n);
        }
        var it = node.fields();
        while (it.hasNext()) {
            JsonNode found = findField(it.next().getValue(), names);
            if (found != null) return found;
        }
        return null;
    }
}
