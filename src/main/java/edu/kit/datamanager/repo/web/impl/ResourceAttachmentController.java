package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ResourceType;
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
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload endpoint used by the web platform; applies the selected resource type policy. */
@RestController
@RequestMapping("/api/v1/dataresources/{id}/attachments")
public class ResourceAttachmentController {
    private static final Set<String> DESCRIPTION_IMAGES = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "tif", "tiff", "bmp");
    private static final Map<ResourceType.TYPE_GENERAL, Set<String>> ALLOWED = Map.of(
            ResourceType.TYPE_GENERAL.IMAGE, Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "tif", "tiff", "bmp"),
            ResourceType.TYPE_GENERAL.TEXT, Set.of("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "epub"),
            ResourceType.TYPE_GENERAL.AUDIOVISUAL, Set.of("mp4", "webm", "mov", "avi", "mkv", "mpeg", "mpg", "m4v"),
            ResourceType.TYPE_GENERAL.DATASET, Set.of("csv", "tsv", "tab", "xls", "xlsx", "ods", "parquet", "sav", "dta", "json", "xml"));
    private final RepoBaseConfiguration repository;
    private final ContentDigestService digests;
    private final edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock;
    public ResourceAttachmentController(RepoBaseConfiguration repository, ContentDigestService digests, edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock) { this.repository = repository; this.digests = digests; this.writeLock = writeLock; }

    @PostMapping(consumes = "multipart/form-data")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> upload(@PathVariable String id, @RequestParam String path,
                                    @RequestParam(name = "package", defaultValue = "false") boolean packageMode,
                                    @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("Debe seleccionar un archivo.");
        String cleanPath = ArchiveUploadPaths.clean(path); if (cleanPath == null) return ResponseEntity.badRequest().body("Ruta de archivo no válida.");
        DataResource resolved = DataResourceUtils.getResourceByIdentifierOrRedirect(repository, id, null, value -> value);
        DataResource resource = writeLock.acquireEditable(resolved.getId());
        Set<String> allowed = ALLOWED.get(resource.getResourceType().getTypeGeneral());
        try {
            if (extension(cleanPath).equals("zip")) {
                List<Entry> entries = unzip(file.getInputStream());
                List<Entry> prepared = prepare(entries, allowed, packageMode);
                for (Entry entry : prepared) {
                    digests.record(ContentDataUtils.addFile(repository, resource, new BytesFile(entry.path(), entry.bytes()), entry.path(), null, true, value -> value));
                }
                return ResponseEntity.noContent().build();
            }
            if (packageMode) return ResponseEntity.badRequest().body("El depósito completo debe ser un ZIP.");
            validate(allowed, cleanPath);
            digests.record(ContentDataUtils.addFile(repository, resource, file, cleanPath, null, true, value -> value));
            return ResponseEntity.noContent().build();
        } catch (IOException ex) { org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); return ResponseEntity.badRequest().body(ex.getMessage()); }
    }
    private List<Entry> prepare(List<Entry> entries, Set<String> allowed, boolean packageMode) throws IOException {
        if (entries.isEmpty()) throw new IOException("El ZIP está vacío.");
        List<Entry> prepared = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        boolean description = false;
        boolean datasetFile = false;
        for (Entry entry : entries) {
            String source = entry.path();
            String target = source;
            if (entry.bytes().length == 0) throw new IOException("El ZIP contiene un archivo vacío: " + source);
            if (source.equals("description/description.md")) {
                target = "description.md";
                description = true;
            } else if (source.startsWith("description/")) {
                if (!DESCRIPTION_IMAGES.contains(extension(source)))
                    throw new IOException("La carpeta description/ solo admite imágenes auxiliares: " + source);
                target = source.substring("description/".length());
            } else if (packageMode) {
                if (source.equals("description.md") || source.contains("/"))
                    throw new IOException("El ZIP completo requiere description/description.md y los datos en la raíz: " + source);
                validate(allowed, source);
                datasetFile = true;
            } else if (!source.equals("description.md")) {
                validate(allowed, source);
            }
            ArchiveUploadPaths.addUnique(targets, target);
            prepared.add(new Entry(target, entry.bytes()));
        }
        if (packageMode && !description) throw new IOException("El ZIP completo debe contener description/description.md.");
        if (packageMode && !datasetFile) throw new IOException("El ZIP completo debe contener al menos un archivo del dataset en su raíz.");
        return prepared;
    }
    private void validate(Set<String> allowed, String path) throws IOException { if (allowed != null && !allowed.contains(extension(path))) throw new IOException("El tipo de recurso no admite archivos ." + extension(path) + "."); }
    private List<Entry> unzip(InputStream source) throws IOException { List<Entry> result=new ArrayList<>(); long total=0; try(ZipInputStream zip=new ZipInputStream(source)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(entry.isDirectory())continue;if(result.size()>=200)throw new IOException("El ZIP contiene demasiados archivos.");String path=ArchiveUploadPaths.clean(entry.getName());if(path==null)throw new IOException("El ZIP contiene una ruta no válida.");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;while((read=zip.read(buffer))!=-1){total+=read;if(total>50L*1024*1024)throw new IOException("El ZIP supera el tamaño permitido.");out.write(buffer,0,read);}result.add(new Entry(path,out.toByteArray()));}}return result; }
    private String extension(String path) { int dot = path.lastIndexOf('.'); return dot < 1 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private record Entry(String path, byte[] bytes) {}
    private static class BytesFile implements MultipartFile { private final String name; private final byte[] bytes; BytesFile(String name,byte[] bytes){this.name=name;this.bytes=bytes;} public String getName(){return "file";} public String getOriginalFilename(){return name;} public String getContentType(){String type=URLConnection.guessContentTypeFromName(name);return type==null?"application/octet-stream":type;} public boolean isEmpty(){return bytes.length==0;} public long getSize(){return bytes.length;} public byte[] getBytes(){return bytes.clone();} public InputStream getInputStream(){return new ByteArrayInputStream(bytes);} public void transferTo(java.io.File destination)throws IOException{java.nio.file.Files.write(destination.toPath(),bytes);} }
}
