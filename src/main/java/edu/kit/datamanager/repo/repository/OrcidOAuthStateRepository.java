package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.OrcidOAuthState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrcidOAuthStateRepository extends JpaRepository<OrcidOAuthState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from OrcidOAuthState state where state.stateHash = :hash")
    Optional<OrcidOAuthState> lockByHash(@Param("hash") String hash);
    void deleteByExpiresAtBefore(Instant before);
}
