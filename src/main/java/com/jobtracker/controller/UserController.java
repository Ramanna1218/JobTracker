package com.jobtracker.controller;

import com.jobtracker.dto.RegisterRequest;
import com.jobtracker.model.User;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.service.DailyJobRunner;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DailyJobRunner dailyJobRunner;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, DailyJobRunner dailyJobRunner) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dailyJobRunner = dailyJobRunner;
    }

    // Register a new friend: email, password, name, resume text, keywords, location
    @PostMapping("/users/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest().body("Email already registered");
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setResumeText(req.getResumeText());
        user.setJobKeywords(req.getJobKeywords());
        user.setLocationPreference(req.getLocationPreference());

        User saved = userRepository.save(user);
        saved.setPasswordHash(null); // never echo the hash back
        return ResponseEntity.ok(saved);
    }

    // Manually trigger a run for a single user (handy for testing without waiting 24h)
    @PostMapping("/users/{id}/run-now")
    public ResponseEntity<?> runNow(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        dailyJobRunner.runForUser(user);
        return ResponseEntity.ok("Run complete - check your email for results");
    }
}
