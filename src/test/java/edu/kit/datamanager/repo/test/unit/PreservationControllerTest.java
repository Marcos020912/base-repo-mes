package edu.kit.datamanager.repo.test.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.FileFixityStateRepository;
import edu.kit.datamanager.repo.repository.FileProvenanceEventRepository;
import edu.kit.datamanager.repo.repository.FixityAuditRunRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.PreservationAuditService;
import edu.kit.datamanager.repo.web.impl.PreservationController;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PreservationControllerTest {
    @Test public void packageContainsFileAndAdmissionManifest() throws Exception {
        var file = Files.createTempFile("reduniv-preservation", ".txt");
        try {
            Files.writeString(file, "abc");
            var records = mock(ScientificRecordRepository.class);
            var resources = mock(IDataResourceDao.class);
            var contents = mock(IContentInformationDao.class);
            var provenance = mock(FileProvenanceEventRepository.class);
            var resource = new DataResource();
            var info = new ContentInformation();
            info.setRelativePath("sample.txt"); info.setContentUri(file.toUri().toString());
            info.setMetadata(Map.of("sha256", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
            when(records.findById("r1")).thenReturn(Optional.of(new ScientificRecord("r1")));
            when(resources.findById("r1")).thenReturn(Optional.of(resource));
            when(contents.findByParentResource(eq(resource), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(info)));
            when(provenance.findByResourceIdOrderByOccurredAtAscIdAsc("r1")).thenReturn(List.of());
            var controller = new PreservationController(mock(PreservationAuditService.class),
                    mock(FixityAuditRunRepository.class), mock(FileFixityStateRepository.class),
                    records, resources, contents, provenance, new ObjectMapper().findAndRegisterModules());
            var response = new MockHttpServletResponse();
            controller.downloadPackage("r1", response);
            var entries = new java.util.HashMap<String, String>();
            try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), new String(zip.readAllBytes()));
            }
            assertEquals("abc", entries.get("data/sample.txt"));
            assertTrue(entries.get("preservation/manifest-sha256.txt").contains("sample.txt"));
            assertTrue(entries.containsKey("preservation/metadata.json"));
            assertTrue(entries.containsKey("preservation/provenance.json"));
        } finally { Files.deleteIfExists(file); }
    }
}
