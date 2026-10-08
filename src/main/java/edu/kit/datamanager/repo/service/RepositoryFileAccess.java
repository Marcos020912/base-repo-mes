package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.ContentInformation;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Resolves a stored file only if its real path remains inside the configured repository. */
@Service
public class RepositoryFileAccess {
    private final Path root;

    public RepositoryFileAccess(@Value("${repo.basepath}") String basepath) {
        URI uri = URI.create(basepath);
        if (!"file".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("repo.basepath debe ser una URL file: para el almacenamiento local.");
        this.root = Path.of(uri).toAbsolutePath().normalize();
    }

    public Path resolve(ContentInformation info) throws IOException {
        String location = info == null ? null : info.getContentUri();
        if (location == null) throw new NoSuchFileException("Archivo del repositorio no disponible.");
        try {
            URI uri = URI.create(location);
            if (!"file".equalsIgnoreCase(uri.getScheme()))
                throw new NoSuchFileException("Archivo del repositorio no disponible.");
            Path rootReal = root.toRealPath();
            Path fileReal = Path.of(uri).toRealPath();
            if (!fileReal.startsWith(rootReal) || !Files.isRegularFile(fileReal) || !Files.isReadable(fileReal))
                throw new NoSuchFileException("Archivo fuera del repositorio o no disponible.");
            return fileReal;
        } catch (IllegalArgumentException error) {
            throw new NoSuchFileException("Ruta del repositorio no válida.");
        }
    }
}
