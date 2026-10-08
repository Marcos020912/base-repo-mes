package edu.kit.datamanager.repo.security;

import edu.kit.datamanager.repo.domain.LocalRole;
import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LocalJwtPasswordVersionTest {
    private final LocalJwtService tokens = new LocalJwtService("test-only-secret-at-least-thirty-two-characters", 480);
    @After public void clearContext() { SecurityContextHolder.clearContext(); }
    private LocalUser user() {
        LocalUser user = new LocalUser("fixture", "unused-hash", LocalRole.USER);
        user.setVerified(true);
        return user;
    }
    private boolean accepted(LocalUser user, String token) throws Exception {
        LocalUserRepository users = mock(LocalUserRepository.class);
        when(users.findByUsernameIgnoreCase("fixture")).thenReturn(Optional.of(user));
        var filter = new LocalJwtAuthenticationFilter(tokens, users, false);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        boolean[] continued = {false};
        filter.doFilter(request, response, (req,res) -> continued[0] = true);
        return continued[0] && response.getStatus() == 200;
    }
    @Test public void freshlyIssuedTokenAcceptsFractionalPasswordTimestamp() throws Exception {
        LocalUser user = user();
        user.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789));
        String token = tokens.create(user);
        assertEquals(user.getPasswordChangedAt().toEpochMilli(), tokens.verify(token).getLongClaim("passwordVersion").longValue());
        assertTrue(accepted(user, token));
    }
    @Test public void previouslyIssuedTokenIsRejectedAfterPasswordChange() throws Exception {
        LocalUser user = user();
        String token = tokens.create(user);
        user.setPasswordChangedAt(Instant.now());
        assertFalse(accepted(user, token));
    }
    @Test public void databaseMicrosecondPrecisionDoesNotInvalidateNewToken() throws Exception {
        LocalUser user = user();
        user.setPasswordChangedAt(Instant.now().plusNanos(123456789));
        String token = tokens.create(user);
        user.setPasswordChangedAt(user.getPasswordChangedAt().truncatedTo(ChronoUnit.MICROS));
        assertTrue(accepted(user, token));
    }
}
