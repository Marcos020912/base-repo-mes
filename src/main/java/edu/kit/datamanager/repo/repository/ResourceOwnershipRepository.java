package edu.kit.datamanager.repo.repository;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResourceOwnershipRepository extends JpaRepository<ResourceOwnership, String> { List<ResourceOwnership> findByUsernameIgnoreCaseOrderByCreatedAtDesc(String username); }
