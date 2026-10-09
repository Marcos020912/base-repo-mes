package edu.kit.datamanager.repo.domain;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Daily pseudonymous, per-link usage. Never stores IP, user agent or account identity. */
@Entity @Table(name="usage_observations",indexes=@Index(name="usage_observations_day_idx",columnList="usageDay"))
@Getter @NoArgsConstructor
public class UsageObservation {
    public enum Kind { VIEW, DOWNLOAD }
    @Id @Column(length=64) private String id;
    @Column(nullable=false,length=255) private String resourceId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private Kind kind;
    @Column(nullable=false) private LocalDate usageDay;
    @Column(nullable=false) private Instant lastSeenAt;
    @Column(nullable=false) private long requests;
    public UsageObservation(String id,String resourceId,Kind kind,Instant at) {
        this.id=id;this.resourceId=resourceId;this.kind=kind;this.usageDay=at.atZone(ZoneOffset.UTC).toLocalDate();
        this.lastSeenAt=at;this.requests=1;
    }
    public void observe(Instant at) {
        if(at.isBefore(lastSeenAt))return;
        if(Duration.between(lastSeenAt,at).compareTo(Duration.ofSeconds(30))>0)requests++;
        lastSeenAt=at;
    }
}
