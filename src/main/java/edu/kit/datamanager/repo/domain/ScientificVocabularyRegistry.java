package edu.kit.datamanager.repo.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
/** Draft proposals never replace the last explicitly approved vocabulary. */
@Entity @Table(name="scientific_vocabulary_registry") @Getter @Setter @NoArgsConstructor
public class ScientificVocabularyRegistry {
    public enum Kind { LICENSE, DISCIPLINE }
    @Id @Enumerated(EnumType.STRING) @Column(length=20) private Kind kind;
    @Version private long revision;
    private boolean approved;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="scientific_vocabulary_approved",joinColumns=@JoinColumn(name="kind")) @OrderColumn(name="position") @Column(name="value",length=255,nullable=false)
    private List<String> approvedValues=new ArrayList<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="scientific_vocabulary_proposed",joinColumns=@JoinColumn(name="kind")) @OrderColumn(name="position") @Column(name="value",length=255,nullable=false)
    private List<String> proposedValues=new ArrayList<>();
    @Column(length=1000) private String proposalNote;
    @Column(length=80) private String proposedBy;
    @Column(length=80) private String approvedBy;
    private Instant proposedAt;
    private Instant approvedAt;
    public ScientificVocabularyRegistry(Kind kind){this.kind=kind;}
}
