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
        return new ScientificResourceWriteLock(entities, database, mock(edu.kit.datamanager.repo.repository.ScientificRecordRepository.class),mock(edu.kit.datamanager.repo.service.SearchIndexOutbox.class));
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
    @Test public void editableUploadRefreshesBeforeCheckingCurrentState() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var database=new DataSourceProperties();database.setUrl("jdbc:h2:mem:fence");
        var records=mock(edu.kit.datamanager.repo.repository.ScientificRecordRepository.class);
        var row=DataResource.factoryNewDataResource("r1");
        when(entities.find(eq(DataResource.class),eq("r1"),eq(LockModeType.PESSIMISTIC_WRITE),anyMap())).thenReturn(row);
        var science=new edu.kit.datamanager.repo.domain.ScientificRecord("r1");
        when(records.findById("r1")).thenReturn(java.util.Optional.of(science));
        var locks=new ScientificResourceWriteLock(entities,database,records,mock(edu.kit.datamanager.repo.service.SearchIndexOutbox.class));
        assertSame(row,locks.acquireEditable("r1"));
        var order=inOrder(entities,records);
        order.verify(entities).find(eq(DataResource.class),eq("r1"),eq(LockModeType.PESSIMISTIC_WRITE),anyMap());
        order.verify(entities).refresh(eq(row),eq(LockModeType.PESSIMISTIC_WRITE),anyMap());
        order.verify(records).findById("r1");
        for(var state:edu.kit.datamanager.repo.domain.PublicationStatus.values()) {
            if(state==edu.kit.datamanager.repo.domain.PublicationStatus.DRAFT)continue;
            science.setStatus(state);
            assertEquals(409,assertThrows(ResponseStatusException.class,()->locks.acquireEditable("r1")).getStatusCode().value());
        }
        // Existing legacy resources without a scientific record retain their ACL flow.
        when(records.findById("r1")).thenReturn(java.util.Optional.empty());
        assertSame(row,locks.acquireEditable("r1"));
    }

}
