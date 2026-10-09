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
    private final edu.kit.datamanager.repo.repository.ScientificRecordRepository records;
    public ScientificResourceWriteLock(EntityManager entities, DataSourceProperties database,
            edu.kit.datamanager.repo.repository.ScientificRecordRepository records) {
        this.entities = entities; this.records = records;
        this.postgres = database.determineUrl().startsWith("jdbc:postgresql:");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DataResource acquire(String id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("Scientific resource lock requires a writable transaction.");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Resource ID required.");
        // PostgreSQL ignores the portable JPA millisecond timeout; use a transaction-local
        // lock wait bound, not a transaction duration bound that would break long uploads.
        if (postgres) entities.createNativeQuery("select set_config('lock_timeout', '2000ms', true)").getSingleResult();
        try {
            DataResource locked = entities.find(DataResource.class, id, LockModeType.PESSIMISTIC_WRITE,
                    Map.of("jakarta.persistence.lock.timeout", 2000));
            if (locked == null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado.");
            return locked;
        } catch (LockTimeoutException | PessimisticLockException busy) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este dataset tiene una operación en curso. Espere y vuelva a intentarlo.");
        }
    }
    /** Recheck editable state under the primary lock, after multipart parsing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public DataResource acquireEditable(String id) {
        DataResource locked = acquire(id);
        // Resolver calls can have populated the persistence context before the row
        // lock was available. Refresh before inspecting upload type/other metadata.
        entities.refresh(locked, LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 2000));
        records.findById(id).ifPresent(record -> {
            if (record.getStatus() != edu.kit.datamanager.repo.domain.PublicationStatus.DRAFT)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El recurso no es un borrador editable. Una versión publicada no puede modificarse ni eliminarse; solicite su retirada.");
        });
        return locked;
    }

}
