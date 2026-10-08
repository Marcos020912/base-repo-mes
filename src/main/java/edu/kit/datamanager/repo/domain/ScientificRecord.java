package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Publication data kept separately from the upstream DataResource model. */
@Entity
@Table(name = "scientific_records")
@Getter
@Setter
@NoArgsConstructor
public class ScientificRecord {
    @Id
    @Column(length = 255)
    private String resourceId;
    @Version
    private long revision;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status = PublicationStatus.DRAFT;
    @Column(length = 40)
    private String versionLabel;
    @Column(length = 255)
    private String versionDoi;
    @Column(length = 255)
    private String conceptualDoi;
    @Column(length = 255)
    private String previousResourceId;
    @Column(length = 100)
    private String licenseId;
    @Column(length = 30)
    private String accessLevel = "OPEN";
    private Instant embargoUntil;
    @Column(length = 16)
    private String language;
    @Column(length = 255)
    private String discipline;
    @Column(length = 2000)
    private String keywords;
    @Column(length = 255)
    private String orcid;
    @Column(length = 255)
    private String institution;
    @Column(length = 255)
    private String ror;
    @Column(length = 2000)
    private String relatedPublications;
    @Column(length = 2000)
    private String methodology;
    @Column(length = 5000)
    private String summary;
    @Column(length = 5000)
    private String productionDescription;
    @Column(length = 5000)
    private String processingDescription;
    @Column(length = 2000)
    private String processingTools;
    private java.time.LocalDate temporalStart;
    private java.time.LocalDate temporalEnd;
    @Column(length = 1000)
    private String geographicCoverage;
    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name="scientific_record_translations",joinColumns=@jakarta.persistence.JoinColumn(name="resource_id"))
    @jakarta.persistence.MapKeyColumn(name="language",length=35)
    private java.util.Map<String,LocalizedScientificMetadata> translations = new java.util.LinkedHashMap<>();
    private Instant submittedAt;
    private Instant publishedAt;
    private Instant withdrawnAt;
    @Column(length = 1000)
    private String withdrawalReason;

    public ScientificRecord(String resourceId) {
        this.resourceId = resourceId;
    }
}
