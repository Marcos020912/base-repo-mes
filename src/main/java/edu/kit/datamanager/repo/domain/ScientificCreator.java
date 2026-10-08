package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Persistent identity of one DataResource creator, never inferred from a shared ORCID field. */
@Entity
@Table(name = "scientific_creators", uniqueConstraints = @UniqueConstraint(columnNames = {"resource_id", "creator_id"}))
@Getter @NoArgsConstructor
public class ScientificCreator {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;
    @Column(length = 255)
    private String orcid;
    @Column(length = 255)
    private String institution;
    @Column(length = 255)
    private String ror;
    /** OAuth proves control of an ORCID account, not that the author's name matches it. */
    @Column(name = "orcid_authenticated_at")
    private Instant orcidAuthenticatedAt;
    @Column(name = "orcid_authenticated_by", length = 80)
    private String orcidAuthenticatedBy;

    public ScientificCreator(String resourceId, Long creatorId, String orcid, String institution, String ror) {
        this.resourceId = resourceId;
        this.creatorId = creatorId;
        this.orcid = orcid;
        this.institution = institution;
        this.ror = ror;
    }

    public void authenticateOrcid(String orcid, String username, Instant when) {
        this.orcid = orcid;
        this.orcidAuthenticatedBy = username;
        this.orcidAuthenticatedAt = when;
    }

    public void retainAuthentication(ScientificCreator previous) {
        if (previous != null && orcid != null && orcid.equals(previous.orcid)) {
            this.orcidAuthenticatedBy = previous.orcidAuthenticatedBy;
            this.orcidAuthenticatedAt = previous.orcidAuthenticatedAt;
        }
    }
}
