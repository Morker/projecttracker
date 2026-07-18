package com.devtrack.app.config;

import com.devtrack.app.user.User;
import com.devtrack.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the default admin user on first startup if not already present.
 * Uses the password from application.yml (app.admin.password).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.admin.username:admin}")
    private String adminUsername;

    @org.springframework.beans.factory.annotation.Value("${app.admin.password:devtrack123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setDisplayName("Admin");
            admin.setRole("ADMIN");
            userRepository.save(admin);
            log.info("Created default admin user: {}", adminUsername);
        }
    }
}
