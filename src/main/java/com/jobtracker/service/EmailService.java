package com.jobtracker.service;

import com.jobtracker.model.JobMatch;
import com.jobtracker.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Lightweight nudge instead of a full digest - the real review happens in the dashboard
    public void sendReviewNotification(User user, List<JobMatch> newMatches) {
        if (newMatches.isEmpty()) {
            log.info("No new matches for {}, skipping notification", user.getEmail());
            return;
        }

        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(user.getFullName()).append(",\n\n");
        body.append(newMatches.size()).append(" new job match(es) are ready for your review.\n\n");
        body.append("Quick preview:\n");
        for (JobMatch m : newMatches) {
            body.append("• ").append(m.getTitle()).append(" @ ").append(m.getCompany())
                    .append(" - match score ").append(m.getMatchScore()).append("/100\n");
        }
        body.append("\nOpen your review dashboard to see tailored bullets and approve or dismiss each one:\n");
        body.append("http://localhost:8080/dashboard.html?userId=").append(user.getId()).append("\n");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject(newMatches.size() + " new job match(es) ready for review");
        message.setText(body.toString());

        mailSender.send(message);
        log.info("Sent review notification ({} matches) to {}", newMatches.size(), user.getEmail());
    }
}
