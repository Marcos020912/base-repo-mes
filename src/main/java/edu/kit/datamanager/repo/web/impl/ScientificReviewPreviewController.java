package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.RepositoryFileAccess;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Curatorial read-only preview independent of the author's legacy ACL. */
@RestController
@RequestMapping("/api/v1/scientific/review")
@PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
public class ScientificReviewPreviewController {
    private final ScientificRecordRepository records;
    private final IDataResourceDao resources;
    private final IContentInformationDao contents;
    private final RepositoryFileAccess fileAccess;

    public ScientificReviewPreviewController(ScientificRecordRepository records, IDataResourceDao resources,
            IContentInformationDao contents, RepositoryFileAccess fileAccess) {
        this.records = records; this.resources = resources; this.contents = contents; this.fileAccess = fileAccess;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ReviewPreview detail(@PathVariable String id) {
        DataResource resource = reviewResource(id);
        String markdown = contents.findByParentResourceAndRelativePath(resource, "description.md")
                .map(info -> {
                    try {
                        Path path = fileAccess.resolve(info);
                        return Files.size(path) <= 1024 * 1024 ? Files.readString(path) : "Descripción demasiado grande para la vista previa.";
                    } catch (IOException ex) { return "No se pudo leer description.md."; }
                }).orElse("No hay description.md.");
        return new ReviewPreview(id,
                resource.getTitles().stream().findFirst().map(item -> item.getValue()).orElse("Sin título"),
                resource.getCreators().stream().map(item -> String.join(" ",
                        item.getGivenName() == null ? "" : item.getGivenName(),
                        item.getFamilyName() == null ? "" : item.getFamilyName()).trim()).toList(),
                resource.getPublisher(), resource.getPublicationYear(), markdown);
    }

    @GetMapping("/{id}/files")
    @Transactional(readOnly = true)
    public ReviewFiles files(@PathVariable String id, @RequestParam(defaultValue = "0") int page) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página no válida.");
        DataResource resource = reviewResource(id);
        var result = contents.findByParentResource(resource, PageRequest.of(page, 100));
        return new ReviewFiles(result.stream()
                .filter(info -> !"description.md".equals(info.getRelativePath()))
                .map(info -> new ReviewFile(info.getRelativePath(), info.getSize(), info.getMediaType(),
                        info.getMetadata() == null ? null : info.getMetadata().get("sha256"))).toList(), result.getTotalPages(), page);
    }

    @GetMapping("/{id}/file")
    public void file(@PathVariable String id, @RequestParam String path, HttpServletResponse response) throws IOException {
        DataResource resource = reviewResource(id);
        ContentInformation info = contents.findByParentResourceAndRelativePath(resource, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Path file;
        try { file = fileAccess.resolve(info); }
        catch (IOException error) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_") + "\"");
        response.setContentLengthLong(Files.size(file));
        try (var input = Files.newInputStream(file)) { input.transferTo(response.getOutputStream()); }
    }

    private DataResource reviewResource(String id) {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getStatus() != PublicationStatus.IN_REVIEW) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public record ReviewPreview(String id, String title, List<String> authors, String publisher, String year, String markdown) {}
    public record ReviewFile(String path, long size, String mediaType, String sha256) {}
    public record ReviewFiles(List<ReviewFile> files, int pages, int page) {}
}
