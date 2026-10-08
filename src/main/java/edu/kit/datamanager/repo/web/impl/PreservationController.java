package edu.kit.datamanager.repo.web.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.FixityAuditRun;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.repository.FileFixityStateRepository;
import edu.kit.datamanager.repo.repository.FileProvenanceEventRepository;
import edu.kit.datamanager.repo.repository.FixityAuditRunRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.BagItManifestBuilder;
import edu.kit.datamanager.repo.service.PreservationAuditService;
import edu.kit.datamanager.repo.service.RoCrateMetadataBuilder;
import edu.kit.datamanager.repo.service.W3cProvExporter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Curatorial preservation operations; packages are deliberately not public downloads. */
@RestController
@RequestMapping("/api/v1/scientific/preservation")
@PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
public class PreservationController {
    private final PreservationAuditService audits;
    private final FixityAuditRunRepository runs;
    private final FileFixityStateRepository fixityStates;
    private final ScientificRecordRepository records;
    private final IDataResourceDao resources;
    private final IContentInformationDao contents;
    private final FileProvenanceEventRepository provenance;
    private final ObjectMapper mapper;

    public PreservationController(PreservationAuditService audits, FixityAuditRunRepository runs,
            FileFixityStateRepository fixityStates, ScientificRecordRepository records,
            IDataResourceDao resources, IContentInformationDao contents,
            FileProvenanceEventRepository provenance, ObjectMapper mapper) {
        this.audits = audits; this.runs = runs; this.fixityStates = fixityStates; this.records = records;
        this.resources = resources; this.contents = contents; this.provenance = provenance; this.mapper = mapper;
    }

    @PostMapping("/audits")
    public ResponseEntity<FixityAuditRun> start() {
        return audits.start().map(run -> ResponseEntity.status(HttpStatus.ACCEPTED).body(run))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una auditoría en curso."));
    }

    @GetMapping("/audits")
    public List<FixityAuditRun> history() { return runs.findTop20ByOrderByStartedAtDesc(); }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        var recent = runs.findTop20ByOrderByStartedAtDesc();
        return Map.of(
                "published", records.countByStatus(PublicationStatus.PUBLISHED),
                "inReview", records.countByStatus(PublicationStatus.IN_REVIEW),
                "files", contents.count(),
                "filesChecked", fixityStates.count(),
                "mismatched", fixityStates.countByStatus("MISMATCH"),
                "missing", fixityStates.countByStatus("MISSING_FILE"),
                "latestAudit", recent.isEmpty() ? Map.of() : recent.get(0));
    }

    @GetMapping("/{id}/package")
    public void downloadPackage(@PathVariable String id, HttpServletResponse response) throws IOException {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Preflight the entire content inventory before writing the first byte of the response.
        List<ContentInformation> files = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int page = 0;
        while (true) {
            var batch = contents.findByParentResource(resource, PageRequest.of(page++, 100));
            for (var file : batch) {
                String relative = file.getRelativePath();
                if (!safeRelativePath(relative) || !names.add(relative)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ruta de contenido no apta para el paquete.");
                }
                if (file.getContentUri() == null || !file.getContentUri().startsWith("file:"))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El paquete contiene almacenamiento no local.");
                Path path;
                try { path = Path.of(URI.create(file.getContentUri())); }
                catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Ruta de almacenamiento inválida."); }
                if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Falta un archivo del paquete.");
                files.add(file);
            }
            if (!batch.hasNext()) break;
        }
        response.setContentType("application/zip");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=preservation-" + id.replaceAll("[^A-Za-z0-9_-]", "_") + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            List<RoCrateMetadataBuilder.PackagedFile> packaged = new ArrayList<>();
            // BagIt requires a payload directory even for a draft with no files.
            zip.putNextEntry(new ZipEntry("data/")); zip.closeEntry();
            byte[] metadata = mapper.writeValueAsBytes(Map.of(
                    "resourceId", id, "record", record, "title", resource.getTitles().stream().findFirst()
                            .map(title -> title.getValue()).orElse("")));
            write(zip, "preservation/metadata.json", metadata);
            packaged.add(packagedBytes("preservation/metadata.json", "application/json", metadata));
            var events = provenance.findByResourceIdOrderByOccurredAtAscIdAsc(id);
            byte[] history = mapper.writeValueAsBytes(events);
            write(zip, "preservation/provenance.json", history);
            packaged.add(packagedBytes("preservation/provenance.json", "application/json", history));
            byte[] prov = mapper.writeValueAsBytes(W3cProvExporter.build(id, events));
            write(zip, "preservation/prov.jsonld", prov);
            packaged.add(packagedBytes("preservation/prov.jsonld", "application/ld+json", prov));
            StringBuilder manifest = new StringBuilder("# SHA-256 al ingreso; no implica comprobación actual\n");
            for (var file : files) {
                String hash = file.getMetadata() == null ? null : file.getMetadata().get("sha256");
                if (hash != null && hash.matches("(?i)[0-9a-f]{64}"))
                    manifest.append(hash).append("  ").append(file.getRelativePath()).append('\n');
                zip.putNextEntry(new ZipEntry("data/" + file.getRelativePath()));
                MessageDigest digest = sha256();
                long size;
                try (var input = new DigestInputStream(Files.newInputStream(Path.of(URI.create(file.getContentUri()))), digest)) {
                    size = input.transferTo(zip);
                }
                zip.closeEntry();
                packaged.add(new RoCrateMetadataBuilder.PackagedFile("data/" + file.getRelativePath(), size,
                        file.getMediaType(), HexFormat.of().formatHex(digest.digest())));
            }
            byte[] baselineManifest = manifest.toString().getBytes(StandardCharsets.UTF_8);
            write(zip, "preservation/manifest-sha256.txt", baselineManifest);
            packaged.add(packagedBytes("preservation/manifest-sha256.txt", "text/plain", baselineManifest));
            byte[] declaration = BagItManifestBuilder.declaration();
            write(zip, "bagit.txt", declaration);
            packaged.add(packagedBytes("bagit.txt", "text/plain", declaration));
            byte[] payloadManifest = BagItManifestBuilder.payloadManifest(packaged);
            write(zip, "manifest-sha256.txt", payloadManifest);
            packaged.add(packagedBytes("manifest-sha256.txt", "text/plain", payloadManifest));
            byte[] crate = mapper.writeValueAsBytes(
                    RoCrateMetadataBuilder.build(id, record, resource, packaged, Instant.now()));
            write(zip, "ro-crate-metadata.json", crate);
            packaged.add(packagedBytes("ro-crate-metadata.json", "application/ld+json", crate));
            write(zip, "tagmanifest-sha256.txt", BagItManifestBuilder.tagManifest(packaged));
        }
    }

    private static void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(data); zip.closeEntry();
    }

    private static RoCrateMetadataBuilder.PackagedFile packagedBytes(String path, String mediaType, byte[] bytes) {
        return new RoCrateMetadataBuilder.PackagedFile(path, bytes.length, mediaType,
                HexFormat.of().formatHex(sha256().digest(bytes)));
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private static boolean safeRelativePath(String relative) {
        if (relative == null || relative.isBlank() || relative.startsWith("/") || relative.contains("\\") ||
                relative.indexOf('\n') >= 0 || relative.indexOf('\r') >= 0 || relative.startsWith("preservation/")) return false;
        try {
            Path normalized = Path.of(relative).normalize();
            return !".".equals(relative) && !normalized.startsWith("..") && normalized.toString().equals(relative);
        } catch (InvalidPathException error) { return false; }
    }
}
