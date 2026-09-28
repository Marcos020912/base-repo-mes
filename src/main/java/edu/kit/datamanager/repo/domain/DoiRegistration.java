package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable intent and latest known DataCite state for a conceptual or version DOI. */
@Entity
@Table(name = "doi_registrations")
@Getter @Setter @NoArgsConstructor
public class DoiRegistration {
    @Id @Column(length = 260)
    private String registrationKey;
    @Version
    private long revision;
    @Column(nullable = false, length = 255)
    private String resourceId;
    @Column(nullable = false, length = 12)
    private String kind;
    @Column(nullable = false, unique = true, length = 255)
    private String doi;
    @Column(nullable = false, length = 128)
    private String apiHost;
    @Column(nullable = false, length = 20)
    private String state;
    private Instant lastSyncedAt;
    @Column(length = 500)
    private String lastError;

    public DoiRegistration(String registrationKey, String resourceId, String kind, String doi, String apiHost) {
        this.registrationKey = registrationKey;
        this.resourceId = resourceId;
        this.kind = kind;
        this.doi = doi;
        this.apiHost = apiHost;
        this.state = "PENDING";
    }
}
