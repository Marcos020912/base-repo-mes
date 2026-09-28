package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Latest independent integrity check; the upload baseline is never changed by verification. */
@Entity
@Table(name = "file_fixity_states")
@Getter @Setter @NoArgsConstructor
public class FileFixityState {
    @Id
    private Long contentId;
    @Column(length = 64)
    private String expectedSha256;
    @Column(length = 64)
    private String actualSha256;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false)
    private Instant checkedAt;

    public FileFixityState(Long contentId, String expectedSha256, String actualSha256, String status, Instant checkedAt) {
        this.contentId = contentId;
        this.expectedSha256 = expectedSha256;
        this.actualSha256 = actualSha256;
        this.status = status;
        this.checkedAt = checkedAt;
    }
}
