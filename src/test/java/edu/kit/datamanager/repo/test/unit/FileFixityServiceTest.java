package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.FileFixityState;
import edu.kit.datamanager.repo.repository.FileFixityStateRepository;
import edu.kit.datamanager.repo.service.FileFixityService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FileFixityServiceTest {
    @Test public void detectsChangedAndMissingFilesWithoutRewritingBaseline() throws Exception {
        Path file = Files.createTempFile("reduniv-fixity", ".txt");
        FileFixityStateRepository repository = mock(FileFixityStateRepository.class);
        when(repository.save(any(FileFixityState.class))).thenAnswer(call -> call.getArgument(0));
        FileFixityService service = new FileFixityService(repository);
        ContentInformation content = new ContentInformation();
        content.setId(42L); content.setRelativePath("data.txt"); content.setContentUri(file.toUri().toString());
        String original = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        content.setMetadata(Map.of("sha256", original));
        try {
            Files.writeString(file, "abc");
            assertEquals("MATCH", service.verify(content).getStatus());
            Files.writeString(file, "changed");
            assertEquals("MISMATCH", service.verify(content).getStatus());
            assertEquals(original, content.getMetadata().get("sha256"));
            Files.delete(file);
            assertEquals("MISSING_FILE", service.verify(content).getStatus());
            content.setMetadata(Map.of());
            assertEquals("NO_BASELINE", service.verify(content).getStatus());
        } finally { Files.deleteIfExists(file); }
    }
}
