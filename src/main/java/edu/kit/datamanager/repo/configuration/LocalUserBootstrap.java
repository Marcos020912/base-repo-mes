package edu.kit.datamanager.repo.configuration;

import edu.kit.datamanager.repo.domain.LocalRole;
import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class LocalUserBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(LocalUserBootstrap.class);
    @Bean CommandLineRunner bootstrapAdmin(LocalUserRepository users, PasswordEncoder passwords,
            @Value("${repo.auth.bootstrap-admin-username:admin}") String username,
            @Value("${repo.auth.bootstrap-admin-password:}") String password) {
        return args -> {
            if (!users.existsByUsernameIgnoreCase(username)) {
                if (password == null || password.isBlank() || "admin12345".equals(password)) {
                    LOG.warn("No bootstrap administrator created: configure a unique repo.auth.bootstrap-admin-password of at least 12 characters.");
                } else if (password.length() < 12) {
                    throw new IllegalStateException("repo.auth.bootstrap-admin-password must contain at least 12 characters");
                } else {
                    LocalUser admin = new LocalUser(username, passwords.encode(password), LocalRole.ADMINISTRATOR);
                    admin.setEmail(username + "@localhost");
                    admin.setVerified(true);
                    users.save(admin);
                    LOG.warn("Created bootstrap administrator '{}'. Rotate its password after first login.", username);
                }
            }
            users.findAll().forEach(user -> {
                if (user.getEmail() == null) {
                    user.setEmail(user.getUsername() + "@local.invalid");
                    user.setVerified(true);
                    users.save(user);
                }
            });
        };
    }
}
