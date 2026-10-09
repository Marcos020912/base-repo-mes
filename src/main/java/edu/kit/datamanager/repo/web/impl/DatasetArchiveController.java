package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.util.ContentDataUtils;
import edu.kit.datamanager.repo.util.DataResourceUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import edu.kit.datamanager.repo.service.RepositoryFileAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates a portable ZIP containing all content files, including description.md. */
@RestController
@RequestMapping("/api/v1/dataresources/{id}/archive")
public class DatasetArchiveController {
    private final RepoBaseConfiguration repository;
    private final RepositoryFileAccess fileAccess;
    public DatasetArchiveController(RepoBaseConfiguration repository, RepositoryFileAccess fileAccess) {
        this.repository = repository; this.fileAccess = fileAccess;
    }
    @GetMapping
    public void download(@PathVariable String id, HttpServletResponse response) throws Exception {
        DataResource resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repository, id, null, value -> value);
        // Preflight before committing headers, using the legacy service's supported
        // bounded pagination (unpaged throws, and page sizes above 100 are clamped).
        java.util.List<ArchiveFile> files = new java.util.ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        int page = 0;
        while (true) {
            var batch = ContentDataUtils.readFiles(repository, resource, "", null, null,
                    PageRequest.of(page++, 100, Sort.by("id")), value -> value);
            for (ContentInformation info : batch) {
                String name = info.getRelativePath();
                if (!PublicDatasetArchiveController.safeRelativePath(name) || !names.add(name))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ruta de contenido no apta para el ZIP.");
                try { files.add(new ArchiveFile(name, fileAccess.resolve(info))); }
                catch (java.io.IOException error) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Archivo del repositorio no disponible.");
                }
            }
            if (batch.size() < 100) break;
        }
        response.setContentType("application/zip");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=dataset-" + id.replaceAll("[^A-Za-z0-9_-]", "_") + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            for (ArchiveFile file : files) {
                zip.putNextEntry(new ZipEntry(file.name()));
                try (InputStream input = Files.newInputStream(file.path())) { input.transferTo(zip); }
                zip.closeEntry();
            }
        }
    }
    private record ArchiveFile(String name, java.nio.file.Path path) {}
}
