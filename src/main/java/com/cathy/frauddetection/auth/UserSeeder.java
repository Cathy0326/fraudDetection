package com.cathy.frauddetection.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial accounts on startup from environment-supplied passwords.
 *
 * <p>Not a Flyway migration: a hash written into a migration file would ship
 * inside a public repository and could be cracked offline. Not a registration
 * endpoint either: nothing in this system lets a stranger choose their own role.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final String analystPassword;

    public UserSeeder(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${seed.admin-password}") String adminPassword,
            @Value("${seed.analyst-password}") String analystPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.analystPassword = analystPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("admin", adminPassword, Role.ADMIN);
        seed("analyst", analystPassword, Role.ANALYST);
    }

    private void seed(String username, String rawPassword, Role role) {
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("No seed password set for '{}'; account not created", username);
            return;
        }
        // Ensure-exists, not create: this runs on every restart, and either
        // overwriting (resetting the password) or inserting again (violating the
        // unique constraint) would be wrong.
        if (repository.findByUsername(username).isPresent()) {
            return;
        }
        repository.save(new UserAccount(username, passwordEncoder.encode(rawPassword), role));
        log.info("Seeded account '{}' with role {}", username, role);
    }
}
