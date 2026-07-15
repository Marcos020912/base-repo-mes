package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resource_ownership")
@Getter
@NoArgsConstructor
public class ResourceOwnership {
    @Id private String resourceId;
    private String username;
    private Instant createdAt = Instant.now();
    public ResourceOwnership(String resourceId, String username) { this.resourceId = resourceId; this.username = username; }
}
