package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.domain.FixityAuditRun;
import edu.kit.datamanager.repo.repository.FixityAuditRunRepository;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Bounded-memory background scan with a PostgreSQL session lock across nodes. */
@Service
public class PreservationAuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreservationAuditService.class);
    private static final String LOCK = "SELECT pg_try_advisory_lock(6413, 7)";
    private static final String UNLOCK = "SELECT pg_advisory_unlock(6413, 7)";
    private final IContentInformationDao contents;
    private final FileFixityService fixity;
    private final FixityAuditRunRepository runs;
    private final DataSource dataSource;
    private final FixityAlertService alerts;
    private final AtomicBoolean localRunning = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "reduniv-fixity-audit"); thread.setDaemon(true); return thread;
    });

    public PreservationAuditService(IContentInformationDao contents, FileFixityService fixity,
                                    FixityAuditRunRepository runs, DataSource dataSource,
                                    FixityAlertService alerts) {
        this.contents = contents; this.fixity = fixity; this.runs = runs;
        this.dataSource = dataSource; this.alerts = alerts;
    }

    /** Empty means an audit is already running on this JVM. */
    public Optional<FixityAuditRun> start() {
        if (!localRunning.compareAndSet(false, true)) return Optional.empty();
        try {
            FixityAuditRun run = runs.save(new FixityAuditRun(UUID.randomUUID().toString()));
            executor.execute(() -> execute(run));
            return Optional.of(run);
        } catch (RuntimeException error) {
            localRunning.set(false);
            throw error;
        }
    }

    private void execute(FixityAuditRun run) {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()))
                throw new IllegalStateException("La auditoría distribuida requiere PostgreSQL.");
            if (!lock(connection)) {
                run.setStatus("SKIPPED");
                run.setMessage("Otra instancia está ejecutando la auditoría.");
            } else {
                try { scan(run); run.setStatus("COMPLETED"); }
                finally { unlock(connection); }
            }
        } catch (Exception error) {
            LOGGER.error("Fixity audit failed", error);
            run.setStatus("FAILED");
            run.setMessage("No se pudo completar la auditoría. Revise el registro del servidor.");
        } finally {
            if ("COMPLETED".equals(run.getStatus()) && (run.getMismatched() > 0 || run.getMissing() > 0)) {
                try { run.setMessage(alerts.notifyAnomalies(run)); }
                catch (RuntimeException error) {
                    LOGGER.error("Could not process fixity alert for audit {}", run.getId(), error);
                    run.setMessage("Incidencias detectadas; falló el procesamiento de la alerta.");
                }
            }
            run.setCompletedAt(Instant.now());
            try { runs.save(run); } catch (RuntimeException error) { LOGGER.error("Could not persist fixity audit result", error); }
            localRunning.set(false);
        }
    }

    private void scan(FixityAuditRun run) {
        long cursor = 0;
        while (true) {
            long lastSeen = cursor;
            var batch = contents.findAll((root, query, cb) -> cb.greaterThan(root.get("id"), lastSeen),
                    PageRequest.of(0, 100, Sort.by("id"))).getContent();
            if (batch.isEmpty()) break;
            for (var file : batch) {
                cursor = file.getId();
                run.setChecked(run.getChecked() + 1);
                try {
                    String result = fixity.verify(file).getStatus();
                    switch (result) {
                        case "MATCH" -> run.setMatched(run.getMatched() + 1);
                        case "MISMATCH" -> { run.setMismatched(run.getMismatched() + 1); LOGGER.warn("Checksum mismatch for content id {}", file.getId()); }
                        case "MISSING_FILE" -> { run.setMissing(run.getMissing() + 1); LOGGER.warn("Missing content id {}", file.getId()); }
                        case "NO_BASELINE" -> run.setNoBaseline(run.getNoBaseline() + 1);
                        case "UNSUPPORTED_URI" -> run.setUnsupported(run.getUnsupported() + 1);
                        default -> { run.setErrors(run.getErrors() + 1); LOGGER.warn("Fixity check returned {} for content id {}", result, file.getId()); }
                    }
                } catch (RuntimeException error) {
                    run.setErrors(run.getErrors() + 1);
                    LOGGER.error("Fixity audit failed for content id {}", file.getId(), error);
                }
            }
            runs.save(run);
        }
        LOGGER.info("Fixity audit {} complete: {} checked, {} mismatches, {} missing, {} errors",
                run.getId(), run.getChecked(), run.getMismatched(), run.getMissing(), run.getErrors());
    }

    private static boolean lock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK); ResultSet result = statement.executeQuery()) {
            return result.next() && result.getBoolean(1);
        }
    }

    private static void unlock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UNLOCK)) { statement.executeQuery().close(); }
    }

    @PreDestroy
    public void shutdown() { executor.shutdown(); }
}
