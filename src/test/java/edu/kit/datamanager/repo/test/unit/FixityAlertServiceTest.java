package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.FixityAuditRun;
import edu.kit.datamanager.repo.service.FixityAlertService;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FixityAlertServiceTest {
    @Test public void noAnomaliesNeverSends() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = create(sender, "alertas@example.org");
        assertNull(service.notifyAnomalies(new FixityAuditRun("run-1")));
        verifyNoInteractions(sender);
    }

    @Test public void disabledRecipientDoesNotSend() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = create(sender, "");
        var audit = new FixityAuditRun("run-2"); audit.setMissing(1);
        assertTrue(service.notifyAnomalies(audit).contains("desactivada"));
        verifyNoInteractions(sender);
    }

    @Test public void sendsOneSummaryWithoutExposingRecipientInHistory() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = create(sender, "alertas@example.org");
        var audit = new FixityAuditRun("run-3");
        audit.setChecked(10); audit.setMismatched(2); audit.setMissing(1);
        String result = service.notifyAnomalies(audit);
        assertFalse(result.contains("alertas@example.org"));
        var capture = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(1)).send(capture.capture());
        assertArrayEquals(new String[]{"alertas@example.org"}, capture.getValue().getTo());
        assertTrue(capture.getValue().getText().contains("Huellas no coincidentes: 2"));
        assertTrue(capture.getValue().getText().contains("Archivos ausentes: 1"));
    }

    @Test public void smtpFailureDoesNotThrowOrClaimSuccess() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("offline")).when(sender).send(any(SimpleMailMessage.class));
        var audit = new FixityAuditRun("run-4"); audit.setMismatched(1);
        assertTrue(create(sender, "alertas@example.org").notifyAnomalies(audit).contains("falló"));
    }

    @Test public void rejectsMultipleOrInvalidRecipientsAtStartup() {
        JavaMailSender sender = mock(JavaMailSender.class);
        assertThrows(IllegalArgumentException.class, () -> create(sender, "uno@example.org,dos@example.org"));
        assertThrows(IllegalArgumentException.class, () -> create(sender, "sin-arroba"));
    }

    @SuppressWarnings("unchecked")
    private static FixityAlertService create(JavaMailSender sender, String recipient) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new FixityAlertService(provider, recipient, "repo@example.org");
    }
}
