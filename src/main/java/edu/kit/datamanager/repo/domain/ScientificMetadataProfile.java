package edu.kit.datamanager.repo.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
/** Approved defaults/rules are separate from proposals; datasets snapshot applied rules. */
@Entity @Table(name="scientific_metadata_profiles") @Getter @Setter @NoArgsConstructor
public class ScientificMetadataProfile {
    @Id @Column(length=50) private String id;
    @Version private long revision;
    private Long approvedRevision;
    private boolean active;
    @Embedded @AttributeOverrides({@AttributeOverride(name="name",column=@Column(name="approved_name",length=100)),@AttributeOverride(name="description",column=@Column(name="approved_description",length=1000)),@AttributeOverride(name="licenseId",column=@Column(name="approved_license_id",length=100)),@AttributeOverride(name="language",column=@Column(name="approved_language",length=16)),@AttributeOverride(name="discipline",column=@Column(name="approved_discipline",length=255))})
    private ScientificMetadataProfileDefinition approved;
    @Embedded @AttributeOverrides({@AttributeOverride(name="name",column=@Column(name="proposed_name",length=100)),@AttributeOverride(name="description",column=@Column(name="proposed_description",length=1000)),@AttributeOverride(name="licenseId",column=@Column(name="proposed_license_id",length=100)),@AttributeOverride(name="language",column=@Column(name="proposed_language",length=16)),@AttributeOverride(name="discipline",column=@Column(name="proposed_discipline",length=255))})
    private ScientificMetadataProfileDefinition proposed;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="scientific_profile_approved_fields",joinColumns=@JoinColumn(name="profile_id")) @Column(name="field_name",length=80,nullable=false)
    private Set<String> approvedRequiredFields=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="scientific_profile_proposed_fields",joinColumns=@JoinColumn(name="profile_id")) @Column(name="field_name",length=80,nullable=false)
    private Set<String> proposedRequiredFields=new LinkedHashSet<>();
    @Column(length=1000) private String proposalNote;
    @Column(length=80) private String proposedBy;
    @Column(length=80) private String approvedBy;
    private Instant proposedAt;
    private Instant approvedAt;
    @Column(length=80) private String updatedBy;
    private Instant updatedAt;
    public ScientificMetadataProfile(String id){this.id=id;}
}
