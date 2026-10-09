package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.AuthRateWindow;
import edu.kit.datamanager.repo.repository.AuthRateWindowRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Database-backed, per-identity and per-client limits shared across application instances. */
@Service
public class AuthRateLimitService {
    private final AuthRateWindowRepository windows;
    private final TransactionTemplate transactions;
    private final byte[] key;
    private final boolean enabled;
    private jakarta.persistence.EntityManager entities;
    private boolean postgres;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthRateLimitService(AuthRateWindowRepository windows, PlatformTransactionManager manager,
            @Value("${repo.auth.jwtSecret}") String secret,
            @Value("${repo.auth.rate-limit.enabled:true}") boolean enabled,
            jakarta.persistence.EntityManager entities, org.springframework.boot.autoconfigure.jdbc.DataSourceProperties database) {
        this(windows, manager, secret, enabled);
        this.entities=entities; this.postgres=database.determineUrl().startsWith("jdbc:postgresql:");
    }

    public AuthRateLimitService(AuthRateWindowRepository windows, PlatformTransactionManager manager,
            @Value("${repo.auth.jwtSecret}") String secret,
            @Value("${repo.auth.rate-limit.enabled:true}") boolean enabled) {
        this.windows = windows;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(5);
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.enabled = enabled;
    }

    public void login(String username, HttpServletRequest request) {
        enforce("login", username, request, 10, 100, 15 * 60);
    }

    public void register(String email, HttpServletRequest request) {
        enforce("register", email, request, 3, 20, 60 * 60);
    }

    public void verify(String email, HttpServletRequest request) {
        enforce("verify", email, request, 10, 100, 15 * 60);
    }

    public void resend(String email, HttpServletRequest request) {
        enforce("resend", email, request, 3, 30, 15 * 60);
    }

    public void changePassword(String username, HttpServletRequest request) {
        enforce("change-password", username, request, 10, 100, 15 * 60);
    }

    private void enforce(String operation, String identity, HttpServletRequest request,
            int identityLimit, int addressLimit, int windowSeconds) {
        if (!enabled) return;
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        // A composite key avoids a global account lockout caused by a different client.
        check(operation + ":account-ip:" + identity.trim().toLowerCase(Locale.ROOT) + ":" + address,
                identityLimit, windowSeconds);
        check(operation + ":ip:" + address, addressLimit, windowSeconds);
    }

    private void check(String source, int maximum, int seconds) {
        String bucketKey = digest(source);
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                long wait = transactions.execute(status -> {
                    if (postgres) entities.createNativeQuery("select set_config('lock_timeout', '2000ms', true)").getSingleResult();
                    Instant now = Instant.now();
                    var existing = windows.lockByKey(bucketKey);
                    if (existing.isEmpty()) {
                        windows.saveAndFlush(new AuthRateWindow(bucketKey, now.plusSeconds(seconds)));
                        return 0L;
                    }
                    AuthRateWindow window = existing.get();
                    if (!window.getExpiresAt().isAfter(now)) {
                        window.setExpiresAt(now.plusSeconds(seconds));
                        window.setAttempts(1);
                        windows.saveAndFlush(window);
                        return 0L;
                    }
                    if (window.getAttempts() >= maximum)
                        return Math.max(1, window.getExpiresAt().getEpochSecond() - now.getEpochSecond());
                    window.setAttempts(window.getAttempts() + 1);
                    windows.saveAndFlush(window);
                    return 0L;
                });
                if (wait > 0) throw new TooManyAttempts(wait);
                return;
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException conflict) {
                if (attempt == 3)
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "No se pudo comprobar el límite de intentos. Intente de nuevo.");
            }
        }
    }

    @Scheduled(cron = "${repo.auth.rate-limit.cleanup-cron:0 15 4 * * *}")
    public void purgeExpired() {
        windows.deleteExpiredBefore(Instant.now().minusSeconds(24 * 60 * 60));
    }

    private String digest(String source) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular la clave del límite de intentos.", ex);
        }
    }

    public static final class TooManyAttempts extends RuntimeException {
        private final long retryAfterSeconds;
        public TooManyAttempts(long retryAfterSeconds) {
            super("Demasiados intentos. Inténtelo más tarde.");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }
}
