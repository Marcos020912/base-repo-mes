package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScientificRecordEventRepository extends JpaRepository<ScientificRecordEvent, Long> {
    List<ScientificRecordEvent> findByResourceIdOrderByCreatedAtAscIdAsc(String resourceId);
}
