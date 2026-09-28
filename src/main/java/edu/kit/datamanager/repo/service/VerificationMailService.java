package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.LocalUser;
import java.time.Instant;
import java.security.SecureRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class VerificationMailService {
    private final JavaMailSender sender;
    private final String from;
    private final SecureRandom random = new SecureRandom();
    public VerificationMailService(ObjectProvider<JavaMailSender> sender, @Value("${repo.mail.from:soporte@mes.gob.cu}") String from) { this.sender=sender.getIfAvailable(); this.from=from; }
    public void createAndSend(LocalUser user) {
        if (sender == null) throw new IllegalStateException("El servidor SMTP no está configurado.");
        String code=String.format("%06d", random.nextInt(1_000_000));
        user.setVerificationCode(code); user.setVerificationExpiresAt(Instant.now().plusSeconds(900));
        SimpleMailMessage mail=new SimpleMailMessage(); mail.setFrom(from); mail.setTo(user.getEmail()); mail.setSubject("Código de verificación · Base Repo"); mail.setText("Su código de verificación es: " + code + "\n\nVence en 15 minutos."); sender.send(mail);
    }
}
