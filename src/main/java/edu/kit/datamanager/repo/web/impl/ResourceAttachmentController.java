package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ResourceType;
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
    private static final Map<ResourceType.TYPE_GENERAL, Set<String>> ALLOWED = Map.of(
            ResourceType.TYPE_GENERAL.IMAGE, Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "tif", "tiff", "bmp"),
            ResourceType.TYPE_GENERAL.TEXT, Set.of("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "epub"),
            ResourceType.TYPE_GENERAL.AUDIOVISUAL, Set.of("mp4", "webm", "mov", "avi", "mkv", "mpeg", "mpg", "m4v"),
            ResourceType.TYPE_GENERAL.DATASET, Set.of("csv", "tsv", "tab", "xls", "xlsx", "ods", "parquet", "sav", "dta", "json", "xml"));
    private final RepoBaseConfiguration repository;
    public ResourceAttachmentController(RepoBaseConfiguration repository) { this.repository = repository; }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@PathVariable String id, @RequestParam String path, @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("Debe seleccionar un archivo.");
        String cleanPath = cleanPath(path); if (cleanPath == null) return ResponseEntity.badRequest().body("Ruta de archivo no válida.");
        DataResource resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repository, id, null, value -> value);
        Set<String> allowed = ALLOWED.get(resource.getResourceType().getTypeGeneral());
        try {
            if (extension(cleanPath).equals("zip")) {
                List<Entry> entries = unzip(file.getInputStream());
                for (Entry entry : entries) {
                    if (entry.path().equals("description.md") || entry.path().startsWith("description/")) continue;
                    validate(allowed, entry.path());
                }
                for (Entry entry : entries) {
                    String target = entry.path().equals("description/description.md") ? "description.md" : entry.path().startsWith("description/") ? entry.path().substring("description/".length()) : entry.path();
                    ContentDataUtils.addFile(repository, resource, new BytesFile(target, entry.bytes()), target, null, true, value -> value);
                }
                return ResponseEntity.noContent().build();
            }
            validate(allowed, cleanPath);
            ContentDataUtils.addFile(repository, resource, file, cleanPath, null, true, value -> value);
            return ResponseEntity.noContent().build();
        } catch (IOException ex) { return ResponseEntity.badRequest().body(ex.getMessage()); }
    }
    private void validate(Set<String> allowed, String path) throws IOException { if (allowed != null && !allowed.contains(extension(path))) throw new IOException("El tipo de recurso no admite archivos ." + extension(path) + "."); }
    private List<Entry> unzip(InputStream source) throws IOException { List<Entry> result=new ArrayList<>(); long total=0; try(ZipInputStream zip=new ZipInputStream(source)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(entry.isDirectory())continue;if(result.size()>=200)throw new IOException("El ZIP contiene demasiados archivos.");String path=cleanPath(Path.of(entry.getName()).normalize().toString());if(path==null)throw new IOException("El ZIP contiene una ruta no válida.");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;while((read=zip.read(buffer))!=-1){total+=read;if(total>50L*1024*1024)throw new IOException("El ZIP supera el tamaño permitido.");out.write(buffer,0,read);}result.add(new Entry(path,out.toByteArray()));}}return result; }
    private String cleanPath(String path) { if (path == null) return null; String value = path.replace('\\', '/'); return value.isBlank() || value.startsWith("/") || value.contains("../") || value.equals("..") ? null : value; }
    private String extension(String path) { int dot = path.lastIndexOf('.'); return dot < 1 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private record Entry(String path, byte[] bytes) {}
    private static class BytesFile implements MultipartFile { private final String name; private final byte[] bytes; BytesFile(String name,byte[] bytes){this.name=name;this.bytes=bytes;} public String getName(){return "file";} public String getOriginalFilename(){return name;} public String getContentType(){String type=URLConnection.guessContentTypeFromName(name);return type==null?"application/octet-stream":type;} public boolean isEmpty(){return bytes.length==0;} public long getSize(){return bytes.length;} public byte[] getBytes(){return bytes.clone();} public InputStream getInputStream(){return new ByteArrayInputStream(bytes);} public void transferTo(java.io.File destination)throws IOException{java.nio.file.Files.write(destination.toPath(),bytes);} }
}
