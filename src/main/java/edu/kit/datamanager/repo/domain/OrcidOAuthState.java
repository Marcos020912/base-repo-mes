package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One-use, short-lived OAuth state. Only its SHA-256 hash is stored. */
@Entity
@Table(name = "orcid_oauth_states")
@Getter @NoArgsConstructor
public class OrcidOAuthState {
    @Id @Column(name = "state_hash", length = 64)
    private String stateHash;
    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;
    @Column(nullable = false, length = 80)
    private String username;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;

    public OrcidOAuthState(String stateHash, String resourceId, Long creatorId, String username, Instant expiresAt) {
        this.stateHash = stateHash;
        this.resourceId = resourceId;
        this.creatorId = creatorId;
        this.username = username;
        this.expiresAt = expiresAt;
    }

    public void consume(Instant when) { consumedAt = when; }
}
