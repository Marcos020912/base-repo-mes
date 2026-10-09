package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.*;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.elastic.*;
import edu.kit.datamanager.repo.repository.SearchIndexTaskRepository;
import jakarta.persistence.*;
import java.util.*;
import org.junit.*;
import org.springframework.data.domain.*;
import org.springframework.transaction.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class SearchIndexWorkerTest {
    private final SearchIndexTaskRepository tasks=mock(SearchIndexTaskRepository.class);
    private final DataResourceRepository search=mock(DataResourceRepository.class);
    private final IContentInformationDao contents=mock(IContentInformationDao.class);
    private final EntityManager entities=mock(EntityManager.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final SearchIndexTask task=new SearchIndexTask("r1");
    private SearchIndexWorker worker;
    private org.springframework.boot.autoconfigure.jdbc.DataSourceProperties database() {
        var db=new org.springframework.boot.autoconfigure.jdbc.DataSourceProperties();db.setUrl("jdbc:h2:mem:index");return db;
    }
    @Before public void setup() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tasks.findTop50ByOrderByCreatedAtAsc()).thenReturn(List.of(task));
        when(tasks.lockTask(task.getId())).thenReturn(Optional.of(task));
        worker=new SearchIndexWorker(tasks,Optional.of(search),mock(IDataResourceDao.class),contents,entities,manager,database());
    }
    @Test public void deletionIsAcknowledgedBeforeRemovingDurableIntent() {
        worker.drain();
        var order=inOrder(search,tasks,manager);
        order.verify(tasks).findTop50ByOrderByCreatedAtAsc();order.verify(manager).getTransaction(any());
        order.verify(tasks).lockTask(task.getId());order.verify(search).deleteById("r1");
        order.verify(tasks).delete(task);order.verify(manager).commit(any());
    }
    @Test public void failedDeliveryRollsBackAndNextRunRetries() {
        doThrow(new IllegalStateException("offline")).doNothing().when(search).deleteById("r1");
        worker.drain();verify(tasks,never()).delete(any(SearchIndexTask.class));verify(manager).rollback(any());
        worker.drain();verify(search,times(2)).deleteById("r1");verify(tasks).delete(task);verify(manager).commit(any());
    }
    @Test public void staleCandidateConsumedByOtherJvmDoesNothing() {
        when(tasks.lockTask(task.getId())).thenReturn(Optional.empty());worker.drain();
        verifyNoInteractions(search,contents,entities);verify(tasks,never()).delete(any(SearchIndexTask.class));
    }
    @Test public void snapshotDoesNotChangeManagedParentAndPagesAllContents() {
        DataResource resource=DataResource.factoryNewDataResource("r1");
        when(entities.find(eq(DataResource.class),eq("r1"),eq(LockModeType.PESSIMISTIC_WRITE),anyMap())).thenReturn(resource);
        ContentInformation info=new ContentInformation();info.setParentResource(resource);info.setRelativePath("datos.csv");
        when(contents.findByParentResource(eq(resource),any(Pageable.class))).thenAnswer(call -> {
            Pageable page=call.getArgument(1);
            return new PageImpl<>(page.getPageNumber()==0?Collections.nCopies(100,info):List.of(info),page,101);
        });
        worker.drain();
        var wrapper=org.mockito.ArgumentCaptor.forClass(ElasticWrapper.class);verify(search).save(wrapper.capture());
        assertEquals(101,((java.util.List<?>)org.springframework.test.util.ReflectionTestUtils.getField(wrapper.getValue(),"content")).size());assertSame(resource,info.getParentResource());
        assertNotSame(resource,org.springframework.test.util.ReflectionTestUtils.getField(wrapper.getValue(),"metadata"));
        assertNotSame(info,((java.util.List<?>)org.springframework.test.util.ReflectionTestUtils.getField(wrapper.getValue(),"content")).get(0));
        verify(contents,times(2)).findByParentResource(eq(resource),any(Pageable.class));
    }
    @Test public void disabledSearchNeverTouchesQueue() {
        worker=new SearchIndexWorker(tasks,Optional.empty(),mock(IDataResourceDao.class),contents,entities,manager,database());
        worker.drain();verifyNoInteractions(tasks,manager);
    }
}
