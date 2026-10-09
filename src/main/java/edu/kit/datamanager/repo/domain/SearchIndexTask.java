package edu.kit.datamanager.repo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An index intent, committed atomically with its SQL mutation. No scientific data snapshot. */
@Entity
@Table(name = "search_index_tasks", indexes = @Index(name = "search_index_tasks_created_idx", columnList = "createdAt"))
@Getter @NoArgsConstructor
public class SearchIndexTask {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 255) private String resourceId;
    @Column(nullable = false) private Instant createdAt;
    public SearchIndexTask(String resourceId) {
        this.id = UUID.randomUUID().toString(); this.resourceId = resourceId; this.createdAt = Instant.now();
    }
}
