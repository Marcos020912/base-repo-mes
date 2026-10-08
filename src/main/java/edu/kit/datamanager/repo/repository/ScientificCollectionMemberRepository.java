package edu.kit.datamanager.repo.repository;
import edu.kit.datamanager.repo.domain.ScientificCollectionMember;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScientificCollectionMemberRepository extends JpaRepository<ScientificCollectionMember,Long> {
    boolean existsByCollectionIdAndResourceId(String collectionId,String resourceId);
    void deleteByCollectionId(String collectionId);
    void deleteByCollectionIdAndResourceId(String collectionId,String resourceId);
}
