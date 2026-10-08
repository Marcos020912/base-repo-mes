package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Structured grant/funder metadata for DataCite and discovery. */
@Entity
@Table(name = "scientific_funding", indexes = @Index(name = "idx_scientific_funding_resource", columnList = "resource_id"))
@Getter @NoArgsConstructor
public class ScientificFunding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "resource_id", nullable = false, length = 255)
    private String resourceId;
    @Column(name = "funder_name", nullable = false, length = 255)
    private String funderName;
    @Column(name = "funder_ror", length = 255)
    private String funderRor;
    @Column(name = "award_number", length = 100)
    private String awardNumber;
    @Column(name = "award_title", length = 500)
    private String awardTitle;

    public ScientificFunding(String resourceId, String funderName, String funderRor, String awardNumber, String awardTitle) {
        this.resourceId = resourceId; this.funderName = funderName; this.funderRor = funderRor;
        this.awardNumber = awardNumber; this.awardTitle = awardTitle;
    }
}
