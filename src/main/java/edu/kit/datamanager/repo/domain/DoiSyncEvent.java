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

/** Append-only audit of attempted DOI transitions and reconciliation. */
@Entity
@Table(name = "doi_sync_events")
@Getter @NoArgsConstructor
public class DoiSyncEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 260)
    private String registrationKey;
    @Column(nullable = false, length = 32)
    private String action;
    @Column(nullable = false, length = 20)
    private String result;
    @Column(nullable = false)
    private Instant occurredAt;
    @Column(length = 500)
    private String detail;

    public DoiSyncEvent(String registrationKey, String action, String result, String detail) {
        this.registrationKey = registrationKey;
        this.action = action;
        this.result = result;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }
}
