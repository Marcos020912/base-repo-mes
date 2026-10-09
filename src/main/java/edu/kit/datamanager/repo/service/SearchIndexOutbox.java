package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.SearchIndexTask;
import edu.kit.datamanager.repo.repository.SearchIndexTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchIndexOutbox {
    private final SearchIndexTaskRepository tasks;
    private final java.util.Optional<edu.kit.datamanager.repo.elastic.DataResourceRepository> search;
    public SearchIndexOutbox(SearchIndexTaskRepository tasks, java.util.Optional<edu.kit.datamanager.repo.elastic.DataResourceRepository> search) { this.tasks = tasks; this.search = search; }
    @Transactional
    public void enqueue(String resourceId) { if (search.isPresent()) tasks.save(new SearchIndexTask(resourceId)); }
}
