package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.FixityAuditRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixityAuditRunRepository extends JpaRepository<FixityAuditRun, String> {
    List<FixityAuditRun> findTop20ByOrderByStartedAtDesc();
}
