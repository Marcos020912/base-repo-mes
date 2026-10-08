package edu.kit.datamanager.repo.repository;
import edu.kit.datamanager.repo.domain.ScientificMetadataProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScientificMetadataProfileRepository extends JpaRepository<ScientificMetadataProfile,String>{
    List<ScientificMetadataProfile> findByActiveTrueOrderByIdAsc();
}
