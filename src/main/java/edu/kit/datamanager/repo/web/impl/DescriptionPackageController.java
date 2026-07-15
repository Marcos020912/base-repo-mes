package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.util.ContentDataUtils;
import edu.kit.datamanager.repo.util.DataResourceUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final RepoBaseConfiguration repository;

    public DescriptionPackageController(RepoBaseConfiguration repository) {
        this.repository = repository;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("Debe seleccionar una descripción.");
        try {
            DataResource resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repository, id, null, value -> value);
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
                ContentDataUtils.addFile(repository, resource, new BytesMultipartFile(entry.path(), entry.bytes()), entry.path(), null, true, value -> value);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResult(files.size(), "description.md"));
        } catch (IOException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    private List<Upload> unpack(InputStream source) throws IOException {
        List<Upload> result = new ArrayList<>(); long total = 0;
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (result.size() >= MAX_ENTRIES) throw new IOException("El ZIP contiene demasiados archivos.");
                String path = safePath(entry.getName());
                byte[] bytes = readEntry(zip, MAX_UNCOMPRESSED_BYTES - total);
                total += bytes.length;
                if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("El ZIP supera el tamaño descomprimido permitido.");
                result.add(new Upload(path, bytes));
            }
        }
        return result;
    }

    private String safePath(String name) throws IOException {
        Path normalized = Path.of(name).normalize();
        String path = normalized.toString().replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.equals("..") || path.startsWith("../")) throw new IOException("El ZIP contiene una ruta no permitida.");
        return path;
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
