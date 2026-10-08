package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An ordered organization attached to one exact creator of one dataset version. */
@Entity
@Table(name = "scientific_affiliations",
        indexes = @Index(name = "idx_scientific_affiliations_resource", columnList = "resource_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_scientific_affiliation_order",
                columnNames = {"resource_id", "creator_id", "sort_order"}))
@Getter @NoArgsConstructor
public class ScientificAffiliation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false, length = 255)
    private String institution;
    @Column(length = 255)
    private String ror;

    public ScientificAffiliation(String resourceId, Long creatorId, int sortOrder,
            String institution, String ror) {
        this.resourceId = resourceId;
        this.creatorId = creatorId;
        this.sortOrder = sortOrder;
        this.institution = institution;
        this.ror = ror;
    }
}
