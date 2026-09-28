package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import org.springframework.stereotype.Service;

/** Captures a SHA-256 baseline after each upload without buffering large files. */
@Service
public class ContentDigestService {
    private final IContentInformationDao contents;
    public ContentDigestService(IContentInformationDao contents) { this.contents = contents; }

    public void record(ContentInformation info) throws IOException {
        if (info.getContentUri() == null || !info.getContentUri().startsWith("file:")) return;
        String digest = calculate(Path.of(URI.create(info.getContentUri())));
        var metadata = new HashMap<>(info.getMetadata() == null ? java.util.Map.<String,String>of() : info.getMetadata());
        metadata.put("sha256", digest);
        info.setMetadata(metadata);
        contents.save(info);
    }

    public static String calculate(Path file) throws IOException {
        final MessageDigest algorithm;
        try { algorithm = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 no está disponible", ex); }
        try (InputStream input = new DigestInputStream(Files.newInputStream(file), algorithm)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return java.util.HexFormat.of().formatHex(algorithm.digest());
    }
}
