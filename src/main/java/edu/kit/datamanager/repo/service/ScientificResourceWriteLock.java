package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.DataResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.util.Map;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Transaction-owned fence shared by scientific state and metadata writers. */
@Service
public class ScientificResourceWriteLock {
    private final EntityManager entities;
    private final boolean postgres;
    public ScientificResourceWriteLock(EntityManager entities, DataSourceProperties database) {
        this.entities = entities;
        this.postgres = database.determineUrl().startsWith("jdbc:postgresql:");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("Scientific resource lock requires a writable transaction.");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Resource ID required.");
        // PostgreSQL ignores the portable JPA millisecond timeout; use a transaction-local
        // lock wait bound, not a transaction duration bound that would break long uploads.
        if (postgres) entities.createNativeQuery("select set_config('lock_timeout', '2000ms', true)").getSingleResult();
        try {
            if (entities.find(DataResource.class, id, LockModeType.PESSIMISTIC_WRITE,
                    Map.of("jakarta.persistence.lock.timeout", 2000)) == null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado.");
        } catch (LockTimeoutException | PessimisticLockException busy) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este dataset tiene una operación en curso. Espere y vuelva a intentarlo.");
        }
    }
}
