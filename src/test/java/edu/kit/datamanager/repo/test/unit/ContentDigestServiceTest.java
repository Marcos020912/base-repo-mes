package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.service.ContentDigestService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ContentDigestServiceTest {
    @Test
    public void calculatesSha256WithoutLoadingWholeFile() throws Exception {
        Path file = Files.createTempFile("reduniv-sha256", ".txt");
        try {
            Files.writeString(file, "abc", StandardCharsets.UTF_8);
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    ContentDigestService.calculate(file));
        } finally { Files.deleteIfExists(file); }
    }
}
