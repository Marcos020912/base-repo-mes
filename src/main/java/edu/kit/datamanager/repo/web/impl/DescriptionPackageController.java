package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.util.ContentDataUtils;
import edu.kit.datamanager.repo.util.DataResourceUtils;
import edu.kit.datamanager.repo.service.ContentDigestService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Receives a project description as description.md or as a safe ZIP package. */
@RestController
@RequestMapping("/api/v1/dataresources/{id}/description")
public class DescriptionPackageController {
    private static final int MAX_ENTRIES = 200;
    private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "tif", "tiff", "bmp");
    private final RepoBaseConfiguration repository;
    private final ContentDigestService digests;
    private final edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock;

    public DescriptionPackageController(RepoBaseConfiguration repository, ContentDigestService digests, edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock) {
        this.repository = repository;
        this.digests = digests; this.writeLock = writeLock;
    }

    @PostMapping(consumes = "multipart/form-data")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> upload(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("Debe seleccionar una descripción.");
        try {
            DataResource resolved = DataResourceUtils.getResourceByIdentifierOrRedirect(repository, id, null, value -> value);
            DataResource resource = writeLock.acquireEditable(resolved.getId());
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            List<Upload> files;
            if (filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                files = unpack(file.getInputStream());
            } else if (filename.equals("description.md")) {
                files = List.of(new Upload("description.md", file.getBytes()));
            } else {
                return ResponseEntity.badRequest().body("El Markdown debe llamarse exactamente description.md, o debe subir un archivo ZIP.");
            }
            if (files.stream().noneMatch(entry -> entry.path().equals("description.md"))) {
                return ResponseEntity.badRequest().body("El ZIP debe contener description.md en su carpeta raíz.");
            }
            for (Upload entry : files) {
                if (entry.bytes().length == 0) return ResponseEntity.badRequest().body("El ZIP contiene un archivo vacío: " + entry.path());
                if (!entry.path().equals("description.md") && !IMAGE_EXTENSIONS.contains(extension(entry.path())))
                    return ResponseEntity.badRequest().body("El ZIP de descripción solo admite description.md e imágenes auxiliares.");
            }
            for (Upload entry : files) {
                digests.record(ContentDataUtils.addFile(repository, resource, new BytesMultipartFile(entry.path(), entry.bytes()), entry.path(), null, true, value -> value));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResult(files.size(), "description.md"));
        } catch (IOException | IllegalArgumentException ex) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    private List<Upload> unpack(InputStream source) throws IOException {
        List<Upload> result = new ArrayList<>(); Set<String> targets = new HashSet<>(); long total = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (result.size() >= MAX_ENTRIES) throw new IOException("El ZIP contiene demasiados archivos.");
                String path = safePath(entry.getName());
                ArchiveUploadPaths.addUnique(targets, path);
                byte[] bytes = readEntry(zip, MAX_UNCOMPRESSED_BYTES - total);
                total += bytes.length;
                if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("El ZIP supera el tamaño descomprimido permitido.");
                result.add(new Upload(path, bytes));
            }
        }
        return result;
    }

    private String safePath(String name) throws IOException {
        String path = ArchiveUploadPaths.clean(name);
        if (path == null) throw new IOException("El ZIP contiene una ruta no permitida.");
        return path;
    }

    private String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 1 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readEntry(InputStream input, long available) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > available) throw new IOException("El ZIP supera el tamaño descomprimido permitido.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private record Upload(String path, byte[] bytes) {}
    public record UploadResult(int uploadedFiles, String description) {}

    private static final class BytesMultipartFile implements MultipartFile {
        private final String filename; private final byte[] bytes;
        BytesMultipartFile(String filename, byte[] bytes) { this.filename = filename; this.bytes = bytes; }
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { String type = URLConnection.guessContentTypeFromName(filename); return type == null ? "application/octet-stream" : type; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File destination) throws IOException { java.nio.file.Files.write(destination.toPath(), bytes); }
    }
}
