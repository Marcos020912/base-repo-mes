package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.FixityAuditRun;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Optional single-message notification after a completed distributed fixity audit. */
@Service
public class FixityAlertService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixityAlertService.class);
    private final JavaMailSender sender;
    private final String recipient;
    private final String from;

    public FixityAlertService(ObjectProvider<JavaMailSender> sender,
            @Value("${repo.fixity.alert-to:}") String recipient,
            @Value("${repo.mail.from:soporte@mes.gob.cu}") String from) {
        this.sender = sender.getIfAvailable();
        this.recipient = recipient == null ? "" : recipient.trim();
        this.from = from;
        if (!this.recipient.isEmpty()) {
            try {
                InternetAddress address = new InternetAddress(this.recipient, true);
                address.validate();
                if (!this.recipient.equals(address.getAddress())) throw new AddressException("Multiple addresses");
            } catch (AddressException error) {
                throw new IllegalArgumentException("repo.fixity.alert-to debe ser una dirección de correo válida.", error);
            }
        }
    }

    /** The returned result is suitable for the audit history; never includes the recipient. */
    public String notifyAnomalies(FixityAuditRun run) {
        if (run.getMismatched() == 0 && run.getMissing() == 0) return null;
        if (recipient.isEmpty()) return "Incidencias detectadas; alerta por correo desactivada.";
        if (sender == null) return "Incidencias detectadas; SMTP no configurado para enviar la alerta.";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("RedUniv: incidencias en auditoría de integridad");
        message.setText("La auditoría de integridad " + run.getId() + " finalizó con incidencias.\n\n"
                + "Archivos comprobados: " + run.getChecked() + "\n"
                + "Huellas no coincidentes: " + run.getMismatched() + "\n"
                + "Archivos ausentes: " + run.getMissing() + "\n\n"
                + "Revise el historial de auditorías en Curación. No modifique la huella original sin investigar.");
        try {
            sender.send(message);
            return "Alerta de integridad enviada por correo.";
        } catch (RuntimeException error) {
            LOGGER.error("Could not send fixity alert for audit {}", run.getId(), error);
            return "Incidencias detectadas; falló el envío de la alerta por correo.";
        }
    }
}
