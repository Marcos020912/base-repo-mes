package edu.kit.datamanager.repo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Optional scheduled entry point; the service coordinates scans across nodes. */
@Component
@ConditionalOnProperty(name = "repo.fixity.enabled", havingValue = "true")
public class ScheduledFixityAudit {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledFixityAudit.class);
    private final PreservationAuditService audits;

    public ScheduledFixityAudit(PreservationAuditService audits) { this.audits = audits; }

    @Scheduled(cron = "${repo.fixity.cron:0 0 3 * * SUN}")
    public void verifyAll() {
        if (audits.start().isEmpty()) LOGGER.info("La auditoría ya está en curso en esta instancia.");
    }
}
