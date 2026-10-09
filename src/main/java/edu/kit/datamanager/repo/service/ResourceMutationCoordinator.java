package edu.kit.datamanager.repo.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Request-scoped mutex independent of editorial transactions and their commits. */
@Service
public class ResourceMutationCoordinator {
    public interface Lease extends AutoCloseable { @Override void close(); }
    private final HikariDataSource lockPool;
    private final ConcurrentHashMap<String,Object> localLocks = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public ResourceMutationCoordinator(DataSourceProperties database,
            @Value("${repo.mutations.lock-pool-size:4}") int poolSize) {
        String url = database.determineUrl();
        if (url.startsWith("jdbc:postgresql:")) {
            if (poolSize < 1 || poolSize > 32) throw new IllegalArgumentException("Mutation lock pool size must be between 1 and 32.");
            HikariConfig config = new HikariConfig();
            config.setPoolName("scientific-mutation-locks");
            config.setJdbcUrl(url); config.setUsername(database.determineUsername()); config.setPassword(database.determinePassword());
            config.setMaximumPoolSize(poolSize); config.setMinimumIdle(0);
            config.setConnectionTimeout(1000); config.setInitializationFailTimeout(-1);
            lockPool = new HikariDataSource(config);
        } else if (url.startsWith("jdbc:h2:") && !url.startsWith("jdbc:h2:tcp:") && !url.startsWith("jdbc:h2:ssl:")
                && !url.toUpperCase(java.util.Locale.ROOT).matches(".*;AUTO_SERVER=(?:TRUE|1)(?:;.*)?")) {
            // Embedded H2 is single-process; multi-instance deployments require PostgreSQL.
            lockPool = null;
        } else throw new IllegalArgumentException("Scientific mutation coordination requires PostgreSQL or local H2.");
    }

    ResourceMutationCoordinator(HikariDataSource lockPool) { this.lockPool = lockPool; }

    public static long key(String id) {
        try {
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
                    .digest(("reduniv:resource-mutation:" + id).getBytes(StandardCharsets.UTF_8))).getLong();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private ResponseStatusException busy() {
        return new ResponseStatusException(HttpStatus.CONFLICT,"Este dataset tiene una operación en curso. Espere y vuelva a intentarlo.");
    }
    public Lease acquire(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Resource ID required.");
        if (lockPool == null) {
            Object token = new Object();
            if (localLocks.putIfAbsent(id,token) != null) throw busy();
            return () -> localLocks.remove(id,token);
        }
        Connection connection = null;
        long key = key(id);
        try {
            connection = lockPool.getConnection();
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1,key); statement.setQueryTimeout(2);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) {
                        connection.close(); connection = null; throw busy();
                    }
                }
            }
            Connection held = connection;
            AtomicBoolean closed = new AtomicBoolean();
            return () -> {
                if (!closed.compareAndSet(false,true)) return;
                try (var statement = held.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1,key); statement.setQueryTimeout(2);
                    try (var result = statement.executeQuery()) {
                        if (!result.next() || !result.getBoolean(1)) lockPool.evictConnection(held);
                    }
                } catch (SQLException failure) {
                    // Never return a session with an uncertain lock to the pool.
                    lockPool.evictConnection(held);
                } finally { try { held.close(); } catch (SQLException ignored) { lockPool.evictConnection(held); } }
            };
        } catch (SQLException failure) {
            if (connection != null) { lockPool.evictConnection(connection); try { connection.close(); } catch (SQLException ignored) {} }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo coordinar la operación. Vuelva a intentarlo más tarde.");
        }
    }
    @PreDestroy public void close() { if (lockPool != null) lockPool.close(); }
}
