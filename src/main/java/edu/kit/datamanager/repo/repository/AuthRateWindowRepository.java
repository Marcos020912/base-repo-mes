package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.AuthRateWindow;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

public interface AuthRateWindowRepository extends JpaRepository<AuthRateWindow, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select window from AuthRateWindow window where window.bucketKey = :key")
    Optional<AuthRateWindow> lockByKey(@Param("key") String key);

    @Modifying
    @Transactional
    @Query("delete from AuthRateWindow window where window.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
