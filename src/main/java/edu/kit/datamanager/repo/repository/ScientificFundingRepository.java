package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.ScientificFunding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScientificFundingRepository extends JpaRepository<ScientificFunding, Long> {
    List<ScientificFunding> findByResourceIdOrderByIdAsc(String resourceId);
    void deleteByResourceId(String resourceId);
}
