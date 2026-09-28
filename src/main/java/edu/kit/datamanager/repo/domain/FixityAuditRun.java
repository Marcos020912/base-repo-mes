package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persisted operational report for one complete fixity scan. */
@Entity
@Table(name = "fixity_audit_runs")
@Getter @Setter @NoArgsConstructor
public class FixityAuditRun {
    @Id @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant completedAt;
    private long checked;
    private long matched;
    private long mismatched;
    private long missing;
    private long noBaseline;
    private long unsupported;
    private long errors;
    @Column(length = 500)
    private String message;

    public FixityAuditRun(String id) {
        this.id = id;
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }
}
