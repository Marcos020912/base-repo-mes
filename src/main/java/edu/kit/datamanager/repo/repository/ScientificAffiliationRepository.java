package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.ScientificAffiliation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScientificAffiliationRepository extends JpaRepository<ScientificAffiliation, Long> {
    List<ScientificAffiliation> findByResourceIdOrderByCreatorIdAscSortOrderAsc(String resourceId);
    void deleteByResourceId(String resourceId);
}
