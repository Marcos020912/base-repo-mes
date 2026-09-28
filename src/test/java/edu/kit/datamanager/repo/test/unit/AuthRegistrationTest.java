package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import edu.kit.datamanager.repo.security.LocalJwtService;
import edu.kit.datamanager.repo.service.VerificationMailService;
import edu.kit.datamanager.repo.web.impl.AuthController;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
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
        AuthController controller = new AuthController(users, passwords, tokens, verification);
        var response = controller.register(new AuthController.RegistrationRequest("researcher", "researcher@example.org", "strong-password"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("VERIFICATION_MAIL_UNAVAILABLE", ((Map<?, ?>) response.getBody()).get("code"));
        verify(users, times(1)).save(any(LocalUser.class));
    }
}
