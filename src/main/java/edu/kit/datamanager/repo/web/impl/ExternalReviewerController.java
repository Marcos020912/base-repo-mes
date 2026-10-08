package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.service.ReviewerAccessService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Anonymous, read-only reviewer access; the secret travels in a request header, not the URL. */
@RestController
@RequestMapping("/api/v1/reviewer")
public class ExternalReviewerController {
    private final ReviewerAccessService access;
    private final IDataResourceDao resources;
    private final IContentInformationDao contents;

    public ExternalReviewerController(ReviewerAccessService access, IDataResourceDao resources,
            IContentInformationDao contents) {
        this.access = access; this.resources = resources; this.contents = contents;
    }

    @GetMapping("/metadata")
    @Transactional(readOnly = true)
    public ReviewMetadata metadata(@RequestHeader("X-Review-Token") String token, HttpServletResponse response) {
        guard(response);
        DataResource resource = resource(token);
        String markdown = contents.findByParentResourceAndRelativePath(resource, "description.md")
                .map(info -> {
                    try {
                        Path path = localPath(info);
                        return Files.size(path) <= 1024 * 1024 ? Files.readString(path) : "Descripción demasiado grande para mostrar.";
                    } catch (IOException error) { return "Descripción no disponible."; }
                }).orElse("No hay description.md.");
        return new ReviewMetadata(resource.getId(),
                resource.getTitles().stream().findFirst().map(item -> item.getValue()).orElse("Sin título"),
                resource.getCreators().stream().map(item -> String.join(" ",
                        item.getGivenName() == null ? "" : item.getGivenName(),
                        item.getFamilyName() == null ? "" : item.getFamilyName()).trim()).toList(),
                resource.getPublisher(), resource.getPublicationYear(), markdown);
    }

    @GetMapping("/files")
    @Transactional(readOnly = true)
    public ReviewFiles files(@RequestHeader("X-Review-Token") String token,
            @RequestParam(defaultValue = "0") int page, HttpServletResponse response) {
        guard(response);
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página no válida.");
        DataResource resource = resource(token);
        var result = contents.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("parentResource"), resource),
                cb.notEqual(root.get("relativePath"), "description.md")), PageRequest.of(page, 100));
        return new ReviewFiles(result.stream()
                .map(info -> new ReviewFile(info.getRelativePath(), info.getSize(), info.getMediaType(),
                        info.getMetadata() == null ? null : info.getMetadata().get("sha256"))).toList(),
                result.getTotalPages(), page);
    }

    @GetMapping("/file")
    public void file(@RequestHeader("X-Review-Token") String token, @RequestParam String path,
            HttpServletResponse response) throws IOException {
        guard(response);
        DataResource resource = resource(token);
        ContentInformation info = contents.findByParentResourceAndRelativePath(resource, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Path file = localPath(info);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_") + "\"");
        response.setContentLengthLong(Files.size(file));
        try (var input = Files.newInputStream(file)) { input.transferTo(response.getOutputStream()); }
    }

    private DataResource resource(String token) {
        String id = access.requireValid(token);
        return resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void guard(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }

    private static Path localPath(ContentInformation info) {
        if (info.getContentUri() == null || !info.getContentUri().startsWith("file:"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return Path.of(URI.create(info.getContentUri()));
    }

    public record ReviewMetadata(String id, String title, List<String> authors, String publisher, String year, String markdown) {}
    public record ReviewFile(String path, long size, String mediaType, String sha256) {}
    public record ReviewFiles(List<ReviewFile> files, int pages, int page) {}
}
