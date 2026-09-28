package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Optional full scan for a single-node deployment; disabled until operators budget its I/O. */
@Component
@ConditionalOnProperty(name = "repo.fixity.enabled", havingValue = "true")
public class ScheduledFixityAudit {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledFixityAudit.class);
    private final IContentInformationDao contents;
    private final FileFixityService fixity;

    public ScheduledFixityAudit(IContentInformationDao contents, FileFixityService fixity) {
        this.contents = contents; this.fixity = fixity;
    }

    @Scheduled(cron = "${repo.fixity.cron:0 0 3 * * SUN}")
    public void verifyAll() {
        int page = 0;
        long checked = 0;
        while (true) {
            var batch = contents.findAll(PageRequest.of(page++, 100, Sort.by("id")));
            for (var file : batch.getContent()) {
                try { fixity.verify(file); checked++; }
                catch (RuntimeException error) { LOGGER.error("Fixity audit failed for content id {}", file.getId(), error); }
            }
            if (!batch.hasNext()) break;
        }
        LOGGER.info("Fixity audit finished: {} files inspected", checked);
    }
}
