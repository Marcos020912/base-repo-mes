package edu.kit.datamanager.repo.test.integration;

import edu.kit.datamanager.repo.elastic.DataResourceRepository;
import edu.kit.datamanager.repo.repository.SearchIndexTaskRepository;
import edu.kit.datamanager.repo.service.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(properties={"repo.search.enabled=false","repo.search.index-initial-delay-ms=3600000"})
@ActiveProfiles("test")
public class SearchIndexOutboxIntegrationTest {
    @Autowired private SearchIndexOutbox outbox;
    @Autowired private SearchIndexTaskRepository tasks;
    @Autowired private SearchIndexWorker worker;
    @Autowired private PlatformTransactionManager manager;
    @MockBean private DataResourceRepository search;
    @Before public void clean() { tasks.deleteAll();reset(search); }
    @Test public void intentSharesSqlCommitAndRollbackBoundary() {
        new TransactionTemplate(manager).executeWithoutResult(status->{outbox.enqueue("rolled-back");status.setRollbackOnly();});
        assertEquals(0,tasks.count());verifyNoInteractions(search);
        new TransactionTemplate(manager).executeWithoutResult(status->outbox.enqueue("deleted-resource"));
        assertEquals(1,tasks.count());verifyNoInteractions(search);
        worker.drain();verify(search).deleteById("deleted-resource");assertEquals(0,tasks.count());
    }
    @Test public void externalFailureKeepsCommittedIntentForRetry() {
        outbox.enqueue("retry-resource");doThrow(new IllegalStateException("offline")).doNothing().when(search).deleteById("retry-resource");
        worker.drain();assertEquals(1,tasks.count());
        worker.drain();assertEquals(0,tasks.count());verify(search,times(2)).deleteById("retry-resource");
    }
}
