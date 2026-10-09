package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.service.ScientificResourceWriteLock;
import jakarta.persistence.*;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScientificResourceWriteLockTest {
    private final EntityManager entities = mock(EntityManager.class);
    private ScientificResourceWriteLock service(String url) {
        var database = new DataSourceProperties(); database.setUrl(url);
        return new ScientificResourceWriteLock(entities, database);
    }
    @After public void cleanup() { TransactionSynchronizationManager.clear(); }
    @Test public void rejectsMissingOrReadOnlyTransactionWithoutTouchingDatabase() {
        var locks = service("jdbc:h2:mem:fence");
        assertThrows(IllegalStateException.class, () -> locks.acquire("r1"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(IllegalStateException.class, () -> locks.acquire("r1"));
        verifyNoInteractions(entities);
    }
    @Test public void holdsPrimaryResourceRowInExistingWritableTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var row = DataResource.factoryNewDataResource("r1");
        when(entities.find(DataResource.class,"r1",LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout",2000))).thenReturn(row);
        service("jdbc:h2:mem:fence").acquire("r1");
        verify(entities).find(DataResource.class,"r1",LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout",2000));
        verifyNoMoreInteractions(entities);
    }
    @Test public void postgresWaitTimeoutIsTransactionLocalNotTransactionDuration() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        Query query=mock(Query.class);
        when(entities.createNativeQuery("select set_config('lock_timeout', '2000ms', true)")).thenReturn(query);
        when(entities.find(eq(DataResource.class),eq("r1"),eq(LockModeType.PESSIMISTIC_WRITE),anyMap()))
                .thenReturn(DataResource.factoryNewDataResource("r1"));
        service("jdbc:postgresql://localhost/fence").acquire("r1");
        var ordered=inOrder(entities,query);
        ordered.verify(entities).createNativeQuery("select set_config('lock_timeout', '2000ms', true)");
        ordered.verify(query).getSingleResult();
        ordered.verify(entities).find(eq(DataResource.class),eq("r1"),eq(LockModeType.PESSIMISTIC_WRITE),anyMap());
    }
    @Test public void contentionAndMissingRowsDoNotProceedSilently() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var locks=service("jdbc:h2:mem:fence");
        assertEquals(404,assertThrows(ResponseStatusException.class,()->locks.acquire("r1")).getStatusCode().value());
        when(entities.find(eq(DataResource.class),eq("r1"),eq(LockModeType.PESSIMISTIC_WRITE),anyMap()))
                .thenThrow(new LockTimeoutException("private SQL details"));
        var denied=assertThrows(ResponseStatusException.class,()->locks.acquire("r1"));
        assertEquals(409,denied.getStatusCode().value());
        assertFalse(denied.getReason().contains("SQL"));
    }
}
