package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.RepositoryFileAccess;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public distribution copy: original files only, never internal preservation or review records. */
@RestController
@RequestMapping("/api/v1/public/resources")
public class PublicDatasetArchiveController {
    private final ScientificRecordRepository records;
    private final IDataResourceDao resources;
    private final IContentInformationDao contents;
    private final RepositoryFileAccess fileAccess;

    public PublicDatasetArchiveController(ScientificRecordRepository records, IDataResourceDao resources,
                                          IContentInformationDao contents, RepositoryFileAccess fileAccess) {
        this.records = records; this.resources = resources; this.contents = contents; this.fileAccess = fileAccess;
    }

    @GetMapping("/{id}/archive")
    @Transactional(readOnly = true)
    public void download(@PathVariable String id, HttpServletResponse response) throws IOException {
        var science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (science.getStatus() != PublicationStatus.PUBLISHED)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        boolean open = "OPEN".equals(science.getAccessLevel()) ||
                ("EMBARGOED".equals(science.getAccessLevel()) && science.getEmbargoUntil() != null
                        && !science.getEmbargoUntil().isAfter(Instant.now()));
        if (!open) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Los archivos no son de acceso abierto.");
        var resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<ArchiveFile> files = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int page = 0;
        while (true) {
            var batch = contents.findByParentResource(resource, PageRequest.of(page++, 100, Sort.by("id")));
            for (ContentInformation info : batch) {
                String name = info.getRelativePath();
                if (!safeRelativePath(name) || !names.add(name))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ruta de contenido no apta para el ZIP.");
                Path path;
                try {
                    path = fileAccess.resolve(info);
                } catch (IOException ex) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Almacenamiento fuera del repositorio o inválido.");
                }
                if (!Files.isReadable(path))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Falta un archivo del dataset.");
                files.add(new ArchiveFile(name, path));
            }
            if (!batch.hasNext()) break;
        }
        response.setContentType("application/zip");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "attachment; filename=dataset-" + id.replaceAll("[^A-Za-z0-9_-]", "_") + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            for (ArchiveFile file : files) {
                zip.putNextEntry(new ZipEntry(file.name()));
                try (var input = Files.newInputStream(file.path())) { input.transferTo(zip); }
                zip.closeEntry();
            }
        }
    }

    static boolean safeRelativePath(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\") ||
                name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) return false;
        try {
            Path normalized = Path.of(name).normalize();
            return !normalized.startsWith("..") && normalized.toString().equals(name) && !".".equals(name);
        } catch (InvalidPathException failure) { return false; }
    }

    private record ArchiveFile(String name, Path path) {}
}
