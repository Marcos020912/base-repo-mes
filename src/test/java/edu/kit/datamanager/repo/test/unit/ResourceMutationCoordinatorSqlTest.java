package edu.kit.datamanager.repo.service;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ResourceMutationCoordinatorSqlTest {
    @Test public void closesLeaseExactlyOnceWithMatchingUnlockKey() throws Exception {
        var pool=mock(HikariDataSource.class);var connection=mock(Connection.class);
        var acquire=mock(PreparedStatement.class);var release=mock(PreparedStatement.class);var acquired=mock(ResultSet.class);var released=mock(ResultSet.class);
        when(pool.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);when(acquire.executeQuery()).thenReturn(acquired);when(acquired.next()).thenReturn(true);when(acquired.getBoolean(1)).thenReturn(true);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);when(release.executeQuery()).thenReturn(released);when(released.next()).thenReturn(true);when(released.getBoolean(1)).thenReturn(true);
        var coordinator=new ResourceMutationCoordinator(pool);var lease=coordinator.acquire("r1");verify(connection,never()).close();lease.close();lease.close();
        verify(acquire).setLong(1,ResourceMutationCoordinator.key("r1"));verify(release).setLong(1,ResourceMutationCoordinator.key("r1"));
        verify(connection).close();verify(pool).getConnection();verifyNoMoreInteractions(pool);
    }
    @Test public void occupiedExternalSessionReturnsConflictAndClosesBorrowedConnection() throws Exception {
        var pool=mock(HikariDataSource.class);var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);var result=mock(ResultSet.class);
        when(pool.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true);when(result.getBoolean(1)).thenReturn(false);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->new ResourceMutationCoordinator(pool).acquire("r1")).getStatusCode().value());
        verify(connection).close();verify(pool,never()).evictConnection(any());
    }
    @Test public void failedAcquireEvictsSessionAndNeverLeaksDriverError() throws Exception {
        var pool=mock(HikariDataSource.class);var connection=mock(Connection.class);when(pool.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("sensitive-driver-error"));
        var error=assertThrows(ResponseStatusException.class,()->new ResourceMutationCoordinator(pool).acquire("r1"));
        assertEquals(503,error.getStatusCode().value());assertFalse(error.getReason().contains("sensitive-driver-error"));verify(pool).evictConnection(connection);verify(connection).close();
    }
    @Test public void uncertainUnlockEvictsRatherThanReturningSessionWithLock() throws Exception {
        var pool=mock(HikariDataSource.class);var connection=mock(Connection.class);var acquire=mock(PreparedStatement.class);var result=mock(ResultSet.class);var release=mock(PreparedStatement.class);
        when(pool.getConnection()).thenReturn(connection);when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);when(acquire.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true);when(result.getBoolean(1)).thenReturn(true);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);when(release.executeQuery()).thenThrow(new SQLException("simulated"));
        var coordinator=new ResourceMutationCoordinator(pool);coordinator.acquire("r1").close();verify(pool).evictConnection(connection);verify(connection).close();
    }
}
