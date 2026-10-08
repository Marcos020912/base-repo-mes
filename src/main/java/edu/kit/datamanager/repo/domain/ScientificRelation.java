package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A machine-readable relationship supplied by the author and checked during curation. */
@Entity
@Table(name = "scientific_relations")
@Getter @NoArgsConstructor
public class ScientificRelation {
    public enum Kind { ARTICLE, SOFTWARE, DATASET, PROJECT, OTHER }
    public enum IdentifierType { DOI, URL }
    public enum RelationType {
        IsCitedBy, Cites, IsSupplementTo, IsSupplementedBy, IsReferencedBy, References,
        IsDocumentedBy, Documents, IsDerivedFrom, IsSourceOf, IsPartOf, HasPart
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String resourceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private IdentifierType identifierType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private RelationType relationType;
    @Column(nullable = false, length = 500)
    private String identifier;
    @Column(length = 255)
    private String title;

    public ScientificRelation(String resourceId, Kind kind, IdentifierType identifierType,
            RelationType relationType, String identifier, String title) {
        this.resourceId = resourceId;
        this.kind = kind;
        this.identifierType = identifierType;
        this.relationType = relationType;
        this.identifier = identifier;
        this.title = title;
    }
}
