package edu.kit.datamanager.repo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "repo_users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
public class LocalUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String username;
    @Column(unique = true, length = 254)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private LocalRole role;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(nullable = false)
    private boolean verified = false;
    @Column(length = 128)
    private String verificationCode;
    private Instant verificationExpiresAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public LocalUser(String username, String passwordHash, LocalRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
}
