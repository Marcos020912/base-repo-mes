package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScientificRecordRepository extends JpaRepository<ScientificRecord, String> {
    List<ScientificRecord> findByStatusOrderBySubmittedAtAsc(PublicationStatus status);
    boolean existsByVersionDoiIgnoreCaseAndResourceIdNot(String versionDoi, String resourceId);
    java.util.Optional<ScientificRecord> findFirstByPreviousResourceIdAndStatusOrderByPublishedAtDesc(String previousResourceId, PublicationStatus status);
}
