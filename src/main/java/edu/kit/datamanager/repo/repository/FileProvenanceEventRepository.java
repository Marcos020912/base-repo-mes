package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.FileProvenanceEvent;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileProvenanceEventRepository extends JpaRepository<FileProvenanceEvent, Long> {
    List<FileProvenanceEvent> findByResourceIdOrderByOccurredAtAscIdAsc(String resourceId);
    Page<FileProvenanceEvent> findByResourceIdOrderByOccurredAtDescIdDesc(String resourceId, Pageable pageable);
}
