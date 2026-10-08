package edu.kit.datamanager.repo.web.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveUploadPathsTest {
    @Test public void acceptsRelativePathsAndNormalizesBackslashes() {
        assertEquals("description/chart.png", ArchiveUploadPaths.clean("description\\chart.png"));
        assertEquals("datos uno.csv", ArchiveUploadPaths.clean("datos uno.csv"));
    }

    @Test public void rejectsTraversalAbsoluteAndAmbiguousPaths() {
        for (String name : new String[]{"../secret.txt", "folder/../secret.txt", "folder/./chart.png",
                "/tmp/file.png", "C:\\temp\\file.png", "folder//chart.png", "folder/", "bad\0name.png",
                "bad\nname.png"}) {
            assertNull(name, ArchiveUploadPaths.clean(name));
        }
    }

    @Test public void rejectsDuplicateAndFileDirectoryCollisions() throws IOException {
        Set<String> targets = new HashSet<>();
        ArchiveUploadPaths.addUnique(targets, "description.md");
        assertConflict(targets, "description.md");
        assertConflict(targets, "description.md/chart.png");
        ArchiveUploadPaths.addUnique(targets, "assets/chart.png");
        assertConflict(targets, "assets");
        ArchiveUploadPaths.addUnique(targets, "assets/figure.png");
    }

    private void assertConflict(Set<String> targets, String path) {
        try {
            ArchiveUploadPaths.addUnique(targets, path);
            fail("Se aceptó la colisión " + path);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("conflicto"));
        }
    }
}
