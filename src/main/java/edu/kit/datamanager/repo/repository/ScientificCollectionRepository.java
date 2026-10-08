package edu.kit.datamanager.repo.repository;
import edu.kit.datamanager.repo.domain.ScientificCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface ScientificCollectionRepository extends JpaRepository<ScientificCollection,String>, JpaSpecificationExecutor<ScientificCollection> {}
