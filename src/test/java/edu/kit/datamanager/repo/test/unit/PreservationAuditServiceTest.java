package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.domain.FixityAuditRun;
import edu.kit.datamanager.repo.domain.FileFixityState;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.repository.FixityAuditRunRepository;
import edu.kit.datamanager.repo.service.FileFixityService;
import edu.kit.datamanager.repo.service.FixityAlertService;
import edu.kit.datamanager.repo.service.PreservationAuditService;
import java.time.Instant;
import java.util.List;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PreservationAuditServiceTest {
    @Test public void persistsSkippedRunWhenAnotherNodeHasTheLock() throws Exception {
        IContentInformationDao contents = mock(IContentInformationDao.class);
        FileFixityService fixity = mock(FileFixityService.class);
        FixityAuditRunRepository runs = mock(FixityAuditRunRepository.class);
        when(runs.save(any(FixityAuditRun.class))).thenAnswer(call -> call.getArgument(0));
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(6413, 7)")).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(false);
        FixityAlertService alerts = mock(FixityAlertService.class);
        PreservationAuditService service = new PreservationAuditService(contents, fixity, runs, dataSource, alerts);
        try {
            assertTrue(service.start().isPresent());
            verify(runs, timeout(3000).atLeast(2)).save(any(FixityAuditRun.class));
            verify(contents, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
            verifyNoInteractions(alerts);
        } finally { service.shutdown(); }
    }

    @Test public void completedAuditNotifiesOnceAfterMismatch() throws Exception {
        IContentInformationDao contents = mock(IContentInformationDao.class);
        FileFixityService fixity = mock(FileFixityService.class);
        FixityAuditRunRepository runs = mock(FixityAuditRunRepository.class);
        FixityAlertService alerts = mock(FixityAlertService.class);
        when(runs.save(any(FixityAuditRun.class))).thenAnswer(call -> call.getArgument(0));
        ContentInformation file = mock(ContentInformation.class);
        when(file.getId()).thenReturn(1L);
        when(contents.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(
                        new PageImpl<>(List.of(file)), new PageImpl<>(List.of()));
        when(fixity.verify(file)).thenReturn(new FileFixityState(1L, "expected", "actual", "MISMATCH", Instant.now()));
        when(alerts.notifyAnomalies(any(FixityAuditRun.class))).thenReturn("Alerta enviada.");
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement unlock = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(6413, 7)")).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(true);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(6413, 7)")).thenReturn(unlock);
        when(unlock.executeQuery()).thenReturn(mock(ResultSet.class));
        PreservationAuditService service = new PreservationAuditService(contents, fixity, runs, dataSource, alerts);
        try {
            FixityAuditRun audit = service.start().orElseThrow();
            verify(alerts, timeout(3000).times(1)).notifyAnomalies(audit);
            verify(runs, timeout(3000).atLeast(3)).save(audit);
            assertEquals("COMPLETED", audit.getStatus());
            assertEquals(1, audit.getMismatched());
            assertEquals("Alerta enviada.", audit.getMessage());
        } finally { service.shutdown(); }
    }
}
