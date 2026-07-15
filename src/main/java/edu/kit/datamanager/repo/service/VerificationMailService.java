package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.LocalUser;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class VerificationMailService {
    private final JavaMailSender sender;
    private final String from;
    public VerificationMailService(JavaMailSender sender, @Value("${repo.mail.from:soporte@mes.gob.cu}") String from) { this.sender=sender; this.from=from; }
    public void createAndSend(LocalUser user) {
        String code=String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        user.setVerificationCode(code); user.setVerificationExpiresAt(Instant.now().plusSeconds(900));
        SimpleMailMessage mail=new SimpleMailMessage(); mail.setFrom(from); mail.setTo(user.getEmail()); mail.setSubject("Código de verificación · Base Repo"); mail.setText("Su código de verificación es: " + code + "\n\nVence en 15 minutos."); sender.send(mail);
    }
}
