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
import lombok.Setter;

/** Append-only history for publication workflow transitions. */
@Entity
@Table(name = "scientific_record_events")
@Getter
@Setter
@NoArgsConstructor
public class ScientificRecordEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String resourceId;
    @Column(nullable = false, length = 100)
    private String actor;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(length = 1000)
    private String detail;

    public ScientificRecordEvent(String resourceId, String actor, String action, String detail) {
        this.resourceId = resourceId;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
        this.createdAt = Instant.now();
    }
}
