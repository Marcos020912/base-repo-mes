package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.FileProvenanceEvent;
import edu.kit.datamanager.repo.repository.FileProvenanceEventRepository;
import edu.kit.datamanager.repo.service.ContentDigestService;
import java.nio.file.Files;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContentDigestProvenanceTest {
    @Test public void recordsAdmissionHashAndAppendOnlyEvent() throws Exception {
        var file = Files.createTempFile("reduniv-provenance", ".txt");
        try {
            Files.writeString(file, "abc");
            var contents = mock(IContentInformationDao.class);
            var events = mock(FileProvenanceEventRepository.class);
            var resource = new DataResource(); resource.setId("r1");
            var info = new ContentInformation(); info.setId(4L); info.setParentResource(resource);
            info.setRelativePath("data.txt"); info.setContentUri(file.toUri().toString());
            new ContentDigestService(contents, events).record(info);
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    info.getMetadata().get("sha256"));
            verify(contents).save(info);
            var event = ArgumentCaptor.forClass(FileProvenanceEvent.class);
            verify(events).save(event.capture());
            assertEquals("STORED", event.getValue().getAction());
            assertEquals("r1", event.getValue().getResourceId());
            assertEquals(info.getMetadata().get("sha256"), event.getValue().getSha256());
        } finally { Files.deleteIfExists(file); }
    }
}
