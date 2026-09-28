package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.FileFixityState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileFixityStateRepository extends JpaRepository<FileFixityState, Long> {
    long countByStatus(String status);
}
