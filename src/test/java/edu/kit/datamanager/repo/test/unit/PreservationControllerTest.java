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
import edu.kit.datamanager.repo.service.RepositoryFileAccess;
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
            info.setRelativePath("sample data%.txt"); info.setContentUri(file.toUri().toString());
            // This admission baseline is intentionally different from the bytes in the ZIP.
            info.setMetadata(Map.of("sha256", "0".repeat(64)));
            when(records.findById("r1")).thenReturn(Optional.of(new ScientificRecord("r1")));
            when(resources.findById("r1")).thenReturn(Optional.of(resource));
            when(contents.findByParentResource(eq(resource), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(info)));
            when(provenance.findByResourceIdOrderByOccurredAtAscIdAsc("r1")).thenReturn(List.of());
            var controller = new PreservationController(mock(PreservationAuditService.class),
                    mock(FixityAuditRunRepository.class), mock(FileFixityStateRepository.class),
                    records, resources, contents, provenance, new ObjectMapper().findAndRegisterModules(),
                    new RepositoryFileAccess(file.getParent().toUri().toString()));
            var response = new MockHttpServletResponse();
            controller.downloadPackage("r1", response);
            var artifact = java.nio.file.Path.of("build/test-artifacts/preservation-ro-crate.zip");
            Files.createDirectories(artifact.getParent());
            Files.write(artifact, response.getContentAsByteArray());
            var entries = new java.util.HashMap<String, String>();
            var entryBytes = new java.util.HashMap<String, byte[]>();
            try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    byte[] bytes = zip.readAllBytes();
                    entryBytes.put(entry.getName(), bytes);
                    entries.put(entry.getName(), new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            assertEquals("abc", entries.get("data/sample data%.txt"));
            assertTrue(entries.get("preservation/manifest-sha256.txt").contains("0".repeat(64)));
            assertTrue(entries.containsKey("preservation/metadata.json"));
            assertTrue(entries.containsKey("preservation/provenance.json"));
            assertTrue(entries.containsKey("preservation/prov.jsonld"));
            assertEquals("BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n", entries.get("bagit.txt"));
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  data/sample data%25.txt\n",
                    entries.get("manifest-sha256.txt"));
            assertTrue(entries.get("tagmanifest-sha256.txt").contains("  ro-crate-metadata.json\n"));
            assertTrue(entries.get("tagmanifest-sha256.txt").contains("  manifest-sha256.txt\n"));
            assertBagManifest(entryBytes, "manifest-sha256.txt", true);
            assertBagManifest(entryBytes, "tagmanifest-sha256.txt", false);
            var prov = new ObjectMapper().readTree(entries.get("preservation/prov.jsonld"));
            assertEquals("http://www.w3.org/ns/prov#", prov.path("@context").path("prov").asText());
            assertTrue(prov.path("@graph").isArray());
            var crate = new ObjectMapper().readTree(entries.get("ro-crate-metadata.json"));
            assertEquals("https://w3id.org/ro/crate/1.2/context", crate.path("@context").asText());
            assertEquals("ro-crate-metadata.json", crate.path("@graph").get(0).path("@id").asText());
            assertEquals("./", crate.path("@graph").get(0).path("about").path("@id").asText());
            assertEquals("Dataset", crate.path("@graph").get(1).path("@type").asText());
            assertEquals("#license", crate.path("@graph").get(1).path("license").path("@id").asText());
            assertTrue(entries.get("ro-crate-metadata.json").contains("No se declara ninguna licencia"));
            assertTrue(crate.path("@graph").get(1).path("datePublished").asText().startsWith("20"));
            for (var part : crate.path("@graph").get(1).path("hasPart")) {
                String path = java.net.URLDecoder.decode(part.path("@id").asText(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue("Referenced file missing from ZIP: " + path, entries.containsKey(path));
            }
            boolean found = false;
            for (var entity : crate.path("@graph")) if (entity.path("@id").asText().equals("data/sample%20data%25.txt")) {
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                        entity.path("sha256").asText());
                assertEquals("3", entity.path("contentSize").asText());
                found = true;
            }
            assertTrue("RO-Crate must describe archived bytes, not the admission baseline", found);
            // A second artifact without '%' is useful for validators that still ignore RFC 8493
            // percent-decoding in manifest paths (e.g. bagit-python 1.9.0).
            info.setRelativePath("plain-data.txt");
            var plainResponse = new MockHttpServletResponse();
            controller.downloadPackage("r1", plainResponse);
            Files.write(artifact.resolveSibling("preservation-bagit-plain.zip"), plainResponse.getContentAsByteArray());
        } finally { Files.deleteIfExists(file); }
    }

    private static void assertBagManifest(Map<String, byte[]> entries, String manifest, boolean payload) throws Exception {
        var listed = new java.util.HashSet<String>();
        for (String line : new String(entries.get(manifest), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
            if (line.isEmpty()) continue;
            String[] fields = line.split("  ", 2);
            assertEquals("BagIt line must contain digest and path", 2, fields.length);
            String path = fields[1].replace("%25", "%").replace("%0D", "\r").replace("%0A", "\n");
            assertTrue("Duplicate BagIt path: " + path, listed.add(path));
            byte[] bytes = entries.get(path);
            assertNotNull("Missing BagIt path: " + path, bytes);
            String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals("Wrong current checksum: " + path, fields[0], actual);
        }
        var expected = new java.util.HashSet<String>();
        for (String path : entries.keySet()) {
            if (payload ? path.startsWith("data/") && !path.equals("data/")
                    : !path.startsWith("data/") && !path.equals("tagmanifest-sha256.txt")) expected.add(path);
        }
        assertEquals("Manifest does not cover every " + (payload ? "payload" : "tag") + " file", expected, listed);
    }
}
