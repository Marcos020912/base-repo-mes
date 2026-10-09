package edu.kit.datamanager.repo.domain;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
@Entity @Table(name="repository_self_assessments") @Getter @Setter @NoArgsConstructor
public class RepositorySelfAssessment {
 public enum State { NOT_STARTED, IN_PROGRESS, READY_FOR_REVIEW, INTERNALLY_REVIEWED }
 @Id @Column(length=3) private String requirementId;
 @Version private long revision;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private State state=State.NOT_STARTED;
 @Column(length=255) private String responsible;
 @Column(length=5000) private String statement;
 @Column(length=4000) private String evidence;
 @Column(nullable=false,length=80) private String updatedBy;
 @Column(nullable=false) private Instant updatedAt;
 public RepositorySelfAssessment(String id){this.requirementId=id;}
}
