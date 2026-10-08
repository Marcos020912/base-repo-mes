package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.service.BagItManifestBuilder;
import edu.kit.datamanager.repo.service.RoCrateMetadataBuilder.PackagedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class BagItManifestBuilderTest {
    @Test public void separatesPayloadAndTagsAndEscapesLiteralPercent() {
        var files = List.of(new PackagedFile("data/sample data%.txt", 3, "text/plain", "a".repeat(64)),
                new PackagedFile("preservation/metadata.json", 10, "application/json", "b".repeat(64)),
                new PackagedFile("manifest-sha256.txt", 70, "text/plain", "c".repeat(64)),
                new PackagedFile("tagmanifest-sha256.txt", 20, "text/plain", "d".repeat(64)));
        String payload = new String(BagItManifestBuilder.payloadManifest(files), StandardCharsets.UTF_8);
        String tags = new String(BagItManifestBuilder.tagManifest(files), StandardCharsets.UTF_8);
        assertEquals("a".repeat(64) + "  data/sample data%25.txt\n", payload);
        assertTrue(tags.contains("c".repeat(64) + "  manifest-sha256.txt\n"));
        assertTrue(tags.contains("b".repeat(64) + "  preservation/metadata.json\n"));
        assertFalse(tags.contains("data/"));
        assertFalse(tags.contains("tagmanifest-sha256.txt"));
        assertEquals("BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n",
                new String(BagItManifestBuilder.declaration(), StandardCharsets.UTF_8));
    }
}
