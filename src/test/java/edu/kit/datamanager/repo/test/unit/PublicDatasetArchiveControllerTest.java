package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.RepositoryFileAccess;
import edu.kit.datamanager.repo.web.impl.PublicDatasetArchiveController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PublicDatasetArchiveControllerTest {
    private final ScientificRecordRepository records = mock(ScientificRecordRepository.class);
    private final IDataResourceDao resources = mock(IDataResourceDao.class);
    private final IContentInformationDao contents = mock(IContentInformationDao.class);
    private final RepositoryFileAccess fileAccess = new RepositoryFileAccess(
            Path.of(System.getProperty("java.io.tmpdir")).toUri().toString());
    private final PublicDatasetArchiveController controller = new PublicDatasetArchiveController(records, resources, contents, fileAccess);

    @Test public void publicZipContainsDescriptionAndDatasetFilesButNoCuratorialRecords() throws Exception {
        var markdown = Files.createTempFile("public-description", ".md");
        var data = Files.createTempFile("public-data", ".csv");
        try {
            Files.writeString(markdown, "# Proyecto"); Files.writeString(data, "a,b\n1,2\n");
            var resource = published("OPEN", null);
            var description = file("description.md", markdown);
            var dataset = file("table.csv", data);
            when(contents.findByParentResource(eq(resource), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(description, dataset)));
            var response = new MockHttpServletResponse();
            controller.download("r1", response);
            assertEquals("application/zip", response.getContentType());
            var entries = new java.util.HashMap<String, String>();
            try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), new String(zip.readAllBytes()));
            }
            assertEquals("# Proyecto", entries.get("description.md"));
            assertEquals("a,b\n1,2\n", entries.get("table.csv"));
            assertEquals(2, entries.size());
            assertFalse(entries.containsKey("preservation/provenance.json"));
        } finally { Files.deleteIfExists(markdown); Files.deleteIfExists(data); }
    }

    @Test public void draftAndRestrictedAreNotDistributed() throws Exception {
        var draft = new ScientificRecord("r1");
        when(records.findById("r1")).thenReturn(Optional.of(draft));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.download("r1", new MockHttpServletResponse())).getStatusCode());
        draft.setStatus(PublicationStatus.PUBLISHED); draft.setAccessLevel("RESTRICTED");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.download("r1", new MockHttpServletResponse())).getStatusCode());
        draft.setAccessLevel("EMBARGOED"); draft.setEmbargoUntil(Instant.now().plusSeconds(3600));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.download("r1", new MockHttpServletResponse())).getStatusCode());
        verifyNoInteractions(contents);
    }

    @Test public void expiredEmbargoAllowsArchive() throws Exception {
        var resource = published("EMBARGOED", Instant.now().minusSeconds(60));
        when(contents.findByParentResource(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var response = new MockHttpServletResponse();
        controller.download("r1", response);
        assertEquals("application/zip", response.getContentType());
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertNull(zip.getNextEntry());
        }
    }

    @Test public void unsafeFileNameFailsBeforeAnyZipBytesAreSent() throws Exception {
        var resource = published("OPEN", null);
        var invalid = new ContentInformation(); invalid.setRelativePath("../secrets.txt");
        when(contents.findByParentResource(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(invalid)));
        var response = new MockHttpServletResponse();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> controller.download("r1", response)).getStatusCode());
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test public void storedFileOutsideRepositoryFailsBeforeZipStarts() throws Exception {
        var resource = published("OPEN", null);
        var outside = file("secret.txt", Path.of("/etc/hosts"));
        when(contents.findByParentResource(eq(resource), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(outside)));
        var response = new MockHttpServletResponse();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> controller.download("r1", response)).getStatusCode());
        assertEquals(0, response.getContentAsByteArray().length);
    }

    private DataResource published(String access, Instant embargo) {
        var science = new ScientificRecord("r1"); science.setStatus(PublicationStatus.PUBLISHED);
        science.setAccessLevel(access); science.setEmbargoUntil(embargo);
        var resource = new DataResource();
        when(records.findById("r1")).thenReturn(Optional.of(science));
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        return resource;
    }

    private static ContentInformation file(String name, java.nio.file.Path path) {
        var info = new ContentInformation(); info.setRelativePath(name); info.setContentUri(path.toUri().toString());
        return info;
    }
}
