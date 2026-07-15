package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.util.ContentDataUtils;
import edu.kit.datamanager.repo.util.DataResourceUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates a portable ZIP containing all content files, including description.md. */
@RestController
@RequestMapping("/api/v1/dataresources/{id}/archive")
public class DatasetArchiveController {
    private final RepoBaseConfiguration repository;
    public DatasetArchiveController(RepoBaseConfiguration repository) { this.repository = repository; }
    @GetMapping
    public void download(@PathVariable String id, HttpServletResponse response) throws Exception {
        DataResource resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repository, id, null, value -> value);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE); response.setHeader("Content-Disposition", "attachment; filename=dataset-" + id + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            for (ContentInformation info : ContentDataUtils.readFiles(repository, resource, "", null, null, Pageable.unpaged(), value -> value)) {
                if (info.getContentUri() == null || !info.getContentUri().startsWith("file:")) continue;
                zip.putNextEntry(new ZipEntry(info.getRelativePath()));
                try (InputStream input = Files.newInputStream(Paths.get(URI.create(info.getContentUri())))) { input.transferTo(zip); }
                zip.closeEntry();
            }
        }
    }
}
