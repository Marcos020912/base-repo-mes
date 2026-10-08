package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.ReviewerAccessLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewerAccessLinkRepository extends JpaRepository<ReviewerAccessLink, Long> {
    Optional<ReviewerAccessLink> findByTokenHash(String tokenHash);
    List<ReviewerAccessLink> findByResourceIdOrderByCreatedAtDesc(String resourceId);
}
