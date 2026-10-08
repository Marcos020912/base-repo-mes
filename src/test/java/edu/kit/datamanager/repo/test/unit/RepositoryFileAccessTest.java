package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.service.RepositoryFileAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.*;

public class RepositoryFileAccessTest {
    @Test public void acceptsOnlyRealFilesUnderConfiguredRoot() throws Exception {
        Path root = Files.createTempDirectory("reduniv-files-root");
        Path outside = Files.createTempFile("reduniv-outside", ".txt");
        Path inside = root.resolve("inside.txt");
        Path escape = root.resolve("escape.txt");
        try {
            Files.writeString(inside, "allowed");
            Files.writeString(outside, "secret");
            Files.createSymbolicLink(escape, outside);
            RepositoryFileAccess access = new RepositoryFileAccess(root.toUri().toString());
            assertEquals(inside.toRealPath(), access.resolve(file(inside.toUri().toString())));
            assertThrows(java.io.IOException.class, () -> access.resolve(file(outside.toUri().toString())));
            assertThrows(java.io.IOException.class, () -> access.resolve(file(escape.toUri().toString())));
            assertThrows(java.io.IOException.class, () -> access.resolve(file("https://example.org/file")));
            assertThrows(java.io.IOException.class, () -> access.resolve(file("file:/missing")));
        } finally {
            Files.deleteIfExists(escape);
            Files.deleteIfExists(inside);
            Files.deleteIfExists(root);
            Files.deleteIfExists(outside);
        }
    }

    private static ContentInformation file(String uri) {
        var content = new ContentInformation(); content.setContentUri(uri); return content;
    }
}
