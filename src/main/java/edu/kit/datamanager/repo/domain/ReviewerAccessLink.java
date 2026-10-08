package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Only a SHA-256 digest is persisted; the bearer token is returned once. */
@Entity
@Table(name = "reviewer_access_links", indexes = {
        @Index(name = "idx_reviewer_access_resource", columnList = "resource_id")})
@Getter @Setter @NoArgsConstructor
public class ReviewerAccessLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public ReviewerAccessLink(String resourceId, String tokenHash, String createdBy, Instant createdAt, Instant expiresAt) {
        this.resourceId = resourceId; this.tokenHash = tokenHash; this.createdBy = createdBy;
        this.createdAt = createdAt; this.expiresAt = expiresAt;
    }
}
