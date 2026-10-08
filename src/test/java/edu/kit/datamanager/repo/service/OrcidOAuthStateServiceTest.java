package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.OrcidOAuthState;
import edu.kit.datamanager.repo.repository.OrcidOAuthStateRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrcidOAuthStateServiceTest {
    @Test public void storesOnlyHashAndRejectsReplay() {
        OrcidOAuthStateRepository repository = mock(OrcidOAuthStateRepository.class);
        OrcidOAuthStateService service = new OrcidOAuthStateService(repository);
        String raw = service.create("resource", 3L, "ana");
        assertEquals(64, raw.length());
        var captor = org.mockito.ArgumentCaptor.forClass(OrcidOAuthState.class);
        verify(repository).save(captor.capture());
        OrcidOAuthState stored = captor.getValue();
        assertNotEquals(raw, stored.getStateHash());
        assertTrue(stored.getExpiresAt().isAfter(Instant.now()));
        when(repository.lockByHash(stored.getStateHash())).thenReturn(Optional.of(stored));
        assertSame(stored, service.consume(raw));
        assertNotNull(stored.getConsumedAt());
        assertThrows(ResponseStatusException.class, () -> service.consume(raw));
    }

    @Test public void rejectsExpiredOrMalformedState() {
        OrcidOAuthStateRepository repository = mock(OrcidOAuthStateRepository.class);
        OrcidOAuthStateService service = new OrcidOAuthStateService(repository);
        assertThrows(ResponseStatusException.class, () -> service.consume("short"));
        verify(repository, never()).lockByHash(any());
        String raw = service.create("resource", 3L, "ana");
        var captor = org.mockito.ArgumentCaptor.forClass(OrcidOAuthState.class);
        verify(repository).save(captor.capture());
        OrcidOAuthState expired = new OrcidOAuthState(captor.getValue().getStateHash(), "resource", 3L,
                "ana", Instant.now().minusSeconds(1));
        when(repository.lockByHash(expired.getStateHash())).thenReturn(Optional.of(expired));
        assertThrows(ResponseStatusException.class, () -> service.consume(raw));
    }
}
