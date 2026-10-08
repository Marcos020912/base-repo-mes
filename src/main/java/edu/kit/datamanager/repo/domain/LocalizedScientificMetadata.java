package edu.kit.datamanager.repo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/** A declared translation, not an automatically inferred language or identity. */
@Embeddable
@Getter @Setter @NoArgsConstructor
public class LocalizedScientificMetadata {
    @Column(name="localized_title",length=500)
    private String title;
    @Column(name="localized_summary",length=5000)
    private String summary;
    public LocalizedScientificMetadata(String title,String summary) {this.title=title;this.summary=summary;}
}
