package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import edu.kit.datamanager.repo.security.LocalJwtService;
import edu.kit.datamanager.repo.service.VerificationMailService;
import edu.kit.datamanager.repo.service.AuthRateLimitService;
import edu.kit.datamanager.repo.web.impl.AuthController;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AuthRegistrationTest {
    @Test public void smtpFailureReportsExistingUnverifiedAccountForRetry() {
        LocalUserRepository users = mock(LocalUserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        LocalJwtService tokens = mock(LocalJwtService.class);
        VerificationMailService verification = mock(VerificationMailService.class);
        when(passwords.encode(any())).thenReturn("hash");
        doThrow(new MailSendException("SMTP unavailable")).when(verification).createAndSend(any(LocalUser.class));
        AuthRateLimitService limits = mock(AuthRateLimitService.class);
        AuthController controller = new AuthController(users, passwords, tokens, verification, limits);
        var response = controller.register(new AuthController.RegistrationRequest("researcher", "researcher@example.org", "strong-password"),
                new MockHttpServletRequest());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("VERIFICATION_MAIL_UNAVAILABLE", ((Map<?, ?>) response.getBody()).get("code"));
        verify(users, times(1)).save(any(LocalUser.class));
    }

    @Test public void throttledRequestsReturn429AndRetryAfter() {
        AuthController controller = new AuthController(mock(LocalUserRepository.class), mock(PasswordEncoder.class),
                mock(LocalJwtService.class), mock(VerificationMailService.class), mock(AuthRateLimitService.class));
        var response = controller.tooManyAttempts(new AuthRateLimitService.TooManyAttempts(120));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("120", response.getHeaders().getFirst("Retry-After"));
    }
}
