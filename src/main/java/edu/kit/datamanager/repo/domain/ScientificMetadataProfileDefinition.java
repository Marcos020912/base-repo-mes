package edu.kit.datamanager.repo.domain;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ScientificMetadataProfileDefinition {
    @Column(length=100) private String name;
    @Column(length=1000) private String description;
    @Column(length=100) private String licenseId;
    @Column(length=16) private String language;
    @Column(length=255) private String discipline;
    public ScientificMetadataProfileDefinition copy(){return new ScientificMetadataProfileDefinition(name,description,licenseId,language,discipline);}
}
