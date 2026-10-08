package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Shared fixed-window counter for sensitive authentication operations. */
@Entity
@Table(name = "auth_rate_windows")
@Getter @Setter @NoArgsConstructor
public class AuthRateWindow {
    @Id
    @Column(name = "bucket_key", length = 64)
    private String bucketKey;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private int attempts;

    public AuthRateWindow(String bucketKey, Instant expiresAt) {
        this.bucketKey = bucketKey;
        this.expiresAt = expiresAt;
        this.attempts = 1;
    }
}
