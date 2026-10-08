package edu.kit.datamanager.repo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="scientific_collection_members", uniqueConstraints=@UniqueConstraint(name="uq_collection_resource",columnNames={"collection_id","resource_id"}),indexes=@Index(name="idx_collection_members_collection",columnList="collection_id"))
@Getter @NoArgsConstructor
public class ScientificCollectionMember {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="collection_id",nullable=false,length=36) private String collectionId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="collection_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_collection_member"))
    private ScientificCollection collection;
    @Column(name="resource_id",nullable=false,length=255) private String resourceId;
    public ScientificCollectionMember(String collectionId,String resourceId) {
        this.collectionId=collectionId; this.resourceId=resourceId;
    }
}
