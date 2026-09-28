package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public landing pages expose released metadata; file access follows the publication access policy. */
@RestController
@RequestMapping("/api/v1/public/resources")
public class PublicScientificResourceController {
    private final IDataResourceDao resources;
    private final ScientificRecordRepository records;
    private final IContentInformationDao contents;

    public PublicScientificResourceController(IDataResourceDao resources, ScientificRecordRepository records, IContentInformationDao contents) {
        this.resources = resources;
        this.records = records;
        this.contents = contents;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> detail(@PathVariable String id) throws IOException {
        ScientificRecord science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        DataResource resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String title = resource.getTitles().stream().findFirst().map(item -> item.getValue()).orElse("Sin título");
        if (science.getStatus() == PublicationStatus.WITHDRAWN) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("title", title, "doi", science.getVersionDoi() == null ? "" : science.getVersionDoi(),
                    "reason", science.getWithdrawalReason() == null ? "Retirado" : science.getWithdrawalReason()));
        }
        requirePublished(science);
        List<String> authors = resource.getCreators().stream().map(item -> String.join(" ",
                item.getGivenName() == null ? "" : item.getGivenName(),
                item.getFamilyName() == null ? "" : item.getFamilyName()).trim()).toList();
        String markdown = contents.findByParentResourceAndRelativePath(resource, "description.md")
                .map(info -> {
                    try {
                        Path path = localPath(info);
                        return Files.size(path) <= 1024 * 1024 ? Files.readString(path) : "Descripción demasiado grande para mostrarla aquí.";
                    } catch (IOException | IllegalArgumentException ex) { return "La descripción no está disponible."; }
                }).orElse("No hay descripción.");
        return ResponseEntity.ok(new PublicDetail(id, title, authors, resource.getPublisher(), resource.getPublicationYear(),
                resource.getResourceType() == null ? null : resource.getResourceType().getTypeGeneral().name(),
                science.getVersionLabel(), science.getVersionDoi(), science.getConceptualDoi(), science.getLicenseId(),
                science.getInstitution(), science.getOrcid(), science.getRor(), science.getLanguage(), science.getDiscipline(),
                science.getKeywords(), science.getRelatedPublications(), science.getMethodology(), science.getPublishedAt(), markdown,
                science.getPreviousResourceId(), science.getAccessLevel(), science.getEmbargoUntil()));
    }

    @GetMapping("/{id}/files")
    @Transactional(readOnly = true)
    public PublicFiles files(@PathVariable String id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginación no válida.");
        DataResource resource = publishedResource(id);
        Page<ContentInformation> result = contents.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("parentResource"), resource), cb.notEqual(root.get("relativePath"), "description.md")), PageRequest.of(page, size));
        return new PublicFiles(result.getContent().stream()
                .map(info -> new FileItem(info.getRelativePath(), info.getSize(), info.getMediaType(),
                        info.getMetadata() == null ? null : info.getMetadata().get("sha256"))).toList(), result.getTotalElements(), result.getNumber(), result.getTotalPages());
    }

    @GetMapping("/{id}/file")
    public void file(@PathVariable String id, @RequestParam String path, @RequestParam(defaultValue = "false") boolean inline,
                     HttpServletResponse response) throws IOException {
        DataResource resource = publishedResource(id);
        ScientificRecord science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean open = "OPEN".equals(science.getAccessLevel()) ||
                ("EMBARGOED".equals(science.getAccessLevel()) && science.getEmbargoUntil() != null && !science.getEmbargoUntil().isAfter(java.time.Instant.now()));
        if (!open) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Los archivos no son de acceso abierto.");
        ContentInformation info = contents.findByParentResourceAndRelativePath(resource, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Path file = localPath(info);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String mediaType = info.getMediaType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : info.getMediaType();
        boolean safeImage = inline && List.of("image/png", "image/jpeg", "image/gif", "image/webp").contains(mediaType);
        response.setContentType(safeImage ? mediaType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", (safeImage ? "inline" : "attachment") + "; filename=\"" + file.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_") + "\"");
        response.setContentLengthLong(Files.size(file));
        try (var input = Files.newInputStream(file)) { input.transferTo(response.getOutputStream()); }
    }

    private DataResource publishedResource(String id) {
        ScientificRecord science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requirePublished(science);
        return resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void requirePublished(ScientificRecord science) {
        if (science.getStatus() != PublicationStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static Path localPath(ContentInformation info) {
        if (info.getContentUri() == null || !info.getContentUri().startsWith("file:")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return Path.of(URI.create(info.getContentUri()));
    }

    public record PublicDetail(String id, String title, List<String> authors, String publisher, String year, String type,
                               String version, String doi, String conceptualDoi, String license, String institution,
                               String orcid, String ror, String language, String discipline, String keywords,
                               String relatedPublications, String methodology, java.time.Instant publishedAt, String markdown,
                               String previousResourceId, String accessLevel, java.time.Instant embargoUntil) {}
    public record FileItem(String path, long size, String mediaType, String sha256) {}
    public record PublicFiles(List<FileItem> files, long total, int page, int pages) {}
}
