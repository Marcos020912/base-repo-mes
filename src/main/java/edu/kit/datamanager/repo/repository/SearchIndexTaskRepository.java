package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.SearchIndexTask;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SearchIndexTaskRepository extends JpaRepository<SearchIndexTask, String> {
    List<SearchIndexTask> findTop50ByOrderByCreatedAtAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SearchIndexTask t where t.id = :id")
    Optional<SearchIndexTask> lockTask(@Param("id") String id);
}
