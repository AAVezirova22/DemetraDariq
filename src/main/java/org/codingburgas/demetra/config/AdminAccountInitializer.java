package org.codingburgas.demetra.config;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/** Provisions the first administrator; public registration never grants ADMIN. */
@Component
public class AdminAccountInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private final UserRepository repository;
    private final UserService users;
    private final String email;
    private final String password;

    public AdminAccountInitializer(UserRepository repository, UserService users,
            @Value("${demetra.admin.email:admin@demetra.local}") String email,
            @Value("${demetra.admin.password:}") String password) {
        this.repository = repository; this.users = users;
        this.email = email.trim().toLowerCase(Locale.ROOT); this.password = password;
    }

    @Override
    public void run(String... args) {
        if (repository.existsByRole(Role.ADMIN)) return;
        if (repository.existsByEmail(email)) {
            throw new IllegalStateException("Admin email is already used by a non-admin account. Set DEMETRA_ADMIN_EMAIL to an unused email.");
        }
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("Set DEMETRA_ADMIN_EMAIL to a valid email address.");
        }
        boolean generated = password.isBlank();
        String initialPassword = password;
        if (generated) {
            byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
            initialPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        } else if (password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("DEMETRA_ADMIN_PASSWORD must contain 12 to 72 characters.");
        }
        users.register("Administrator", email, initialPassword, Role.ADMIN);
        log.info("Created administrator account: {}", email);
        if (generated) log.warn("Initial admin password (shown only on first startup): {}", initialPassword);
    }
}
