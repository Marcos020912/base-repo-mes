package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.DoiRegistration;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoiRegistrationRepository extends JpaRepository<DoiRegistration, String> {
    List<DoiRegistration> findByResourceId(String resourceId);
}
