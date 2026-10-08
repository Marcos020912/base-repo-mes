package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.OrcidOAuthState;
import edu.kit.datamanager.repo.repository.OrcidOAuthStateRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrcidOAuthStateService {
    private final OrcidOAuthStateRepository states;
    private final SecureRandom random = new SecureRandom();

    public OrcidOAuthStateService(OrcidOAuthStateRepository states) { this.states = states; }

    @Transactional
    public String create(String resourceId, Long creatorId, String username) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        states.save(new OrcidOAuthState(hash(state), resourceId, creatorId, username, Instant.now().plusSeconds(600)));
        return state;
    }

    /** Commit consumption before the network exchange so a callback can never be replayed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrcidOAuthState consume(String state) {
        if (state == null || !state.matches("[0-9a-f]{64}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado ORCID no válido.");
        OrcidOAuthState entry = states.lockByHash(hash(state))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud ORCID no encontrada."));
        Instant now = Instant.now();
        if (entry.getConsumedAt() != null || !entry.getExpiresAt().isAfter(now))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud ORCID vencida o utilizada.");
        entry.consume(now);
        return entry;
    }

    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void deleteExpired() { states.deleteByExpiresAtBefore(Instant.now().minusSeconds(86400)); }

    private static String hash(String state) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
