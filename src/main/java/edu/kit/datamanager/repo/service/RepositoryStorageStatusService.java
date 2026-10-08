package edu.kit.datamanager.repo.service;

import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Capacity of the configured local volume, not a claim about repository quotas/backups. */
@Service
public class RepositoryStorageStatusService {
    private final String basepath;
    public RepositoryStorageStatusService(@Value("${repo.basepath}") String basepath){this.basepath=basepath;}
    public record StorageStatus(String status,Long totalBytes,Long usableBytes,Long unallocatedBytes,Instant measuredAt,String scope){}
    public StorageStatus status(){
        Instant at=Instant.now();String scope="Volumen local del almacenamiento configurado; incluye espacio usado por otros servicios. No representa cuota ni tamaño de los datasets.";
        try{
            URI uri=URI.create(basepath);
            if(!"file".equalsIgnoreCase(uri.getScheme()))return new StorageStatus("UNSUPPORTED",null,null,null,at,scope);
            Path path=Path.of(uri);if(!Files.isDirectory(path)||!Files.isReadable(path))return new StorageStatus("UNAVAILABLE",null,null,null,at,scope);
            FileStore store=Files.getFileStore(path);
            return new StorageStatus("AVAILABLE",store.getTotalSpace(),store.getUsableSpace(),store.getUnallocatedSpace(),at,scope);
        }catch(Exception failure){return new StorageStatus("UNAVAILABLE",null,null,null,at,scope);}
    }
}
