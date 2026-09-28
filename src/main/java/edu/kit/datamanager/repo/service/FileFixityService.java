package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.FileFixityState;
import edu.kit.datamanager.repo.repository.FileFixityStateRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recomputes SHA-256 from the stored file and compares it with the upload-time baseline. */
@Service
public class FileFixityService {
    private final FileFixityStateRepository states;

    public FileFixityService(FileFixityStateRepository states) { this.states = states; }

    @Transactional
    public FileFixityState verify(ContentInformation info) {
        String expected = info.getMetadata() == null ? null : info.getMetadata().get("sha256");
        String actual = null;
        String status;
        if (expected == null || !expected.matches("(?i)[0-9a-f]{64}")) {
            status = "NO_BASELINE";
        } else if (info.getContentUri() == null || !info.getContentUri().startsWith("file:")) {
            status = "UNSUPPORTED_URI";
        } else {
            try {
                Path path = Path.of(URI.create(info.getContentUri()));
                if (!Files.isRegularFile(path)) status = "MISSING_FILE";
                else {
                    actual = ContentDigestService.calculate(path);
                    status = expected.equalsIgnoreCase(actual) ? "MATCH" : "MISMATCH";
                }
            } catch (IOException | IllegalArgumentException error) {
                status = "READ_ERROR";
            }
        }
        return states.save(new FileFixityState(info.getId(), expected == null ? null : expected.toLowerCase(Locale.ROOT), actual, status, Instant.now()));
    }
}
