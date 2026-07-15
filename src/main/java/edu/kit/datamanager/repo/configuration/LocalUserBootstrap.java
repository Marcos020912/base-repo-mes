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
            @Value("${repo.auth.bootstrap-admin-password:admin12345}") String password) {
        return args -> { if (!users.existsByUsernameIgnoreCase(username)) { LocalUser admin=new LocalUser(username, passwords.encode(password), LocalRole.ADMINISTRATOR); admin.setEmail(username + "@localhost"); admin.setVerified(true); users.save(admin); LOG.warn("Created bootstrap administrator '{}'. Change its password immediately.", username); } users.findAll().forEach(user -> { if (user.getEmail()==null) { user.setEmail(user.getUsername()+"@local.invalid"); user.setVerified(true); users.save(user); } }); };
    }
}
