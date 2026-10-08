package edu.kit.datamanager.repo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/** Private editorial assessment. Never part of a public metadata DTO. */
@Entity @Table(name="scientific_privacy_assessments")
@Getter @Setter @NoArgsConstructor
public class ScientificPrivacyAssessment {
    public enum Classification { NONE, PERSONAL, CONFIDENTIAL }
    public enum ReviewState { UNREVIEWED, APPROVED, CHANGES_REQUIRED }
    @Id @Column(length=255) private String resourceId;
    @Version private long revision;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Classification classification;
    @Column(length=2000) private String assessmentNote;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReviewState reviewState=ReviewState.UNREVIEWED;
    private Instant updatedAt;
    private Instant reviewedAt;
    @Column(length=80) private String reviewedBy;
    @Column(length=1000) private String reviewNote;
    public ScientificPrivacyAssessment(String id) {resourceId=id;}
    public void clearReview() {reviewState=ReviewState.UNREVIEWED;reviewedAt=null;reviewedBy=null;reviewNote=null;}
}
