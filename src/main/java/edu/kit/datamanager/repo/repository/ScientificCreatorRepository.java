package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.ScientificCreator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScientificCreatorRepository extends JpaRepository<ScientificCreator, Long> {
    List<ScientificCreator> findByResourceId(String resourceId);
    Optional<ScientificCreator> findByResourceIdAndCreatorId(String resourceId, Long creatorId);
    void deleteByResourceId(String resourceId);
}
