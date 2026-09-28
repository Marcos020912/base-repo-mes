package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only account of web uploads and deletions; never stores file bytes or credentials. */
@Entity
@Table(name = "file_provenance_events")
@Getter @NoArgsConstructor
public class FileProvenanceEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String resourceId;
    private Long contentId;
    @Column(nullable = false, length = 1024)
    private String relativePath;
    @Column(nullable = false, length = 24)
    private String action;
    @Column(nullable = false, length = 100)
    private String actor;
    @Column(length = 64)
    private String sha256;
    @Column(nullable = false)
    private Instant occurredAt;

    public FileProvenanceEvent(String resourceId, Long contentId, String relativePath,
                               String action, String actor, String sha256) {
        this.resourceId = resourceId;
        this.contentId = contentId;
        this.relativePath = relativePath;
        this.action = action;
        this.actor = actor;
        this.sha256 = sha256;
        this.occurredAt = Instant.now();
    }
}
