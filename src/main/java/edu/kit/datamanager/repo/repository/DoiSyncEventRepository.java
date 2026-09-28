package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.DoiSyncEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoiSyncEventRepository extends JpaRepository<DoiSyncEvent, Long> {
    List<DoiSyncEvent> findByRegistrationKeyOrderByOccurredAtAscIdAsc(String registrationKey);
}
