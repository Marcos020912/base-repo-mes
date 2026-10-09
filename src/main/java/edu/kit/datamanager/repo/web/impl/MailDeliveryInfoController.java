package edu.kit.datamanager.repo.web.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public delivery instructions, never credentials or verification codes. */
@RestController
public class MailDeliveryInfoController {
    private final DeliveryInfo info;
    public MailDeliveryInfoController(
            @Value("${repo.mail.delivery-mode:SMTP}") String mode,
            @Value("${repo.mail.preview-url:}") String previewUrl) {
        if (!Set.of("SMTP", "LOCAL_CAPTURE").contains(mode))
            throw new IllegalArgumentException("Unsupported mail delivery mode");
        String preview = null;
        if ("LOCAL_CAPTURE".equals(mode) && !previewUrl.isBlank()) {
            URI uri = URI.create(previewUrl);
            if (!"http".equals(uri.getScheme()) || uri.getHost() == null || !Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
                    || uri.getPort() > 65535 || uri.getPort() == 0
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
                throw new IllegalArgumentException("Mail preview must be a local HTTP viewer without credentials or parameters");
            preview = uri.toString();
        }
        info = new DeliveryInfo(mode, preview);
    }
    @GetMapping("/api/v1/public/mail-delivery")
    public DeliveryInfo delivery() { return info; }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeliveryInfo(String mode, String previewUrl) {}
}
