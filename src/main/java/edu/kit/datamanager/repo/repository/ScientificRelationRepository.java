package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.ScientificRelation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScientificRelationRepository extends JpaRepository<ScientificRelation, Long> {
    List<ScientificRelation> findByResourceIdOrderByIdAsc(String resourceId);
    void deleteByResourceId(String resourceId);
}
