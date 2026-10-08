package edu.kit.datamanager.repo.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="scientific_collections")
@Getter @Setter @NoArgsConstructor
public class ScientificCollection {
    public enum Kind { THEMATIC, INSTITUTIONAL }
    @Id @Column(length=36) private String id;
    @Version private long revision;
    @Column(nullable=false,length=200) private String title;
    @Column(length=2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Kind kind;
    @Column(nullable=false) private boolean published;
    public ScientificCollection(String title, String description, Kind kind, boolean published) {
        this.id=UUID.randomUUID().toString(); this.title=title; this.description=description;
        this.kind=kind; this.published=published;
    }
}
