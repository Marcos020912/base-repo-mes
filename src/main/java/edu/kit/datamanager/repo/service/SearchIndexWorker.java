package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.dao.*;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.elastic.*;
import edu.kit.datamanager.repo.repository.SearchIndexTaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** At-least-once delivery of committed SQL state; retries survive process restarts. */
@Service
public class SearchIndexWorker {
    private final SearchIndexTaskRepository tasks;
    private final Optional<DataResourceRepository> search;
    private final IContentInformationDao contents;
    private final EntityManager entities;
    private final boolean postgres;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    public SearchIndexWorker(SearchIndexTaskRepository tasks, Optional<DataResourceRepository> search,
            IDataResourceDao resources, IContentInformationDao contents, EntityManager entities,
            PlatformTransactionManager manager, org.springframework.boot.autoconfigure.jdbc.DataSourceProperties database) {
        this.tasks=tasks; this.search=search; this.contents=contents; this.entities=entities;
        this.postgres=database.determineUrl().startsWith("jdbc:postgresql:");
        this.transactions=new TransactionTemplate(manager); this.transactions.setTimeout(30);
    }
    @Scheduled(fixedDelayString="${repo.search.index-retry-delay-ms:5000}", initialDelayString="${repo.search.index-initial-delay-ms:5000}")
    public void drain() {
        if (search.isEmpty()) return;
        for (SearchIndexTask candidate : tasks.findTop50ByOrderByCreatedAtAsc()) {
            try { transactions.executeWithoutResult(status -> deliver(candidate.getId())); }
            catch (RuntimeException failure) {
                // Keep the durable intent. Do not expose endpoints, credentials or driver errors.
                LoggerFactory.getLogger(getClass()).warn("Search indexing deferred for task {}; automatic retry pending.", candidate.getId());
            }
        }
    }
    void deliver(String taskId) {
        if (postgres) entities.createNativeQuery("select set_config('lock_timeout', '2000ms', true)").getSingleResult();
        SearchIndexTask task=tasks.lockTask(taskId).orElse(null);
        if (task==null) return; // another JVM already completed this intent
        // Lock before reading a snapshot; all resource writers use the same primary row.
        // Keeping this lock through the bounded ES request prevents stale publication/deletion writes.
        DataResource resource=entities.find(DataResource.class, task.getResourceId(), LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout",2000));
        if (resource==null) search.orElseThrow().deleteById(task.getResourceId());
        else {
            entities.refresh(resource,LockModeType.PESSIMISTIC_WRITE);
            List<ContentInformation> detached=new ArrayList<>();
            int page=0;
            org.springframework.data.domain.Page<ContentInformation> batch;
            do {
                batch=contents.findByParentResource(resource,PageRequest.of(page++,100,Sort.by("id")));
                for(ContentInformation original:batch) {
                    ContentInformation copy=mapper.convertValue(original,ContentInformation.class);
                    copy.setParentResource(DataResource.factoryNewDataResource(resource.getId()));
                    detached.add(copy);
                }
            } while(batch.hasNext());
            DataResource snapshot=mapper.convertValue(resource,DataResource.class);
            search.orElseThrow().save(new ElasticWrapper(snapshot,detached));
        }
        tasks.delete(task); // only after ES acknowledgement; rollback leaves an idempotent retry
    }
}
