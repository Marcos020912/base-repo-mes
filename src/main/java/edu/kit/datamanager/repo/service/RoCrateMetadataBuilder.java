package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal attached RO-Crate 1.2 metadata for the bytes actually written into a preservation ZIP. */
public final class RoCrateMetadataBuilder {
    private RoCrateMetadataBuilder() {}

    public static Map<String, Object> build(String resourceId, ScientificRecord science, DataResource resource,
            List<PackagedFile> files, Instant packagedAt) {
        String title = resource.getTitles().stream().findFirst().map(item -> item.getValue()).orElse("Recurso " + resourceId);
        Map<String, Object> descriptor = Map.of("@id", "ro-crate-metadata.json", "@type", "CreativeWork",
                "conformsTo", ref("https://w3id.org/ro/crate/1.2"), "about", ref("./"));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@id", "./"); root.put("@type", "Dataset"); root.put("name", title);
        String summary = resource.getDescriptions().stream().map(item -> item.getDescription())
                .filter(value -> value != null && !value.isBlank()).findFirst()
                .orElse("Paquete curatorial de preservación del recurso " + resourceId + ".");
        root.put("description", summary);
        root.put("datePublished", science.getStatus() == PublicationStatus.PUBLISHED && science.getPublishedAt() != null
                ? science.getPublishedAt().toString() : packagedAt.toString());
        root.put("identifier", ref("#reduniv-resource-id"));
        // A missing licence must not be presented as permission to reuse the data.
        String license = science.getLicenseId() == null || science.getLicenseId().isBlank()
                ? "Licencia no informada" : science.getLicenseId();
        root.put("license", ref("#license"));
        if (science.getVersionLabel() != null) root.put("version", science.getVersionLabel());
        root.put("publisher", ref("#publisher"));
        if (science.getStatus() == PublicationStatus.PUBLISHED && science.getVersionDoi() != null
                && science.getVersionDoi().matches("10\\.[0-9]{4,9}/\\S+"))
            root.put("sameAs", "https://doi.org/" + science.getVersionDoi());
        List<Map<String, String>> parts = new ArrayList<>();
        List<Map<String, Object>> graph = new ArrayList<>();
        graph.add(descriptor); graph.add(root);
        graph.add(Map.of("@id", "#reduniv-resource-id", "@type", "PropertyValue",
                "name", "Datos RedUniv", "value", resourceId));
        graph.add(Map.of("@id", "#publisher", "@type", "Organization", "name",
                resource.getPublisher() == null || resource.getPublisher().isBlank() ? "Datos RedUniv" : resource.getPublisher()));
        graph.add(Map.of("@id", "#license", "@type", "CreativeWork", "name", license,
                "description", science.getLicenseId() == null || science.getLicenseId().isBlank()
                        ? "No se declara ninguna licencia; este paquete no concede permisos de reutilización."
                        : "Licencia declarada por el depositante; consulte sus condiciones oficiales."));
        int personNumber = 0;
        List<Map<String, String>> authors = new ArrayList<>();
        for (var creator : resource.getCreators()) {
            String name = ((creator.getGivenName() == null ? "" : creator.getGivenName()) + " "
                    + (creator.getFamilyName() == null ? "" : creator.getFamilyName())).trim();
            if (name.isBlank()) continue;
            String id = "#creator-" + ++personNumber;
            authors.add(ref(id));
            graph.add(Map.of("@id", id, "@type", "Person", "name", name));
        }
        if (!authors.isEmpty()) root.put("author", authors);
        for (PackagedFile file : files) {
            String id = uriPath(file.path());
            parts.add(ref(id));
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("@id", id); entity.put("@type", "File");
            entity.put("name", file.path().substring(file.path().lastIndexOf('/') + 1));
            entity.put("description", description(file.path()));
            entity.put("contentSize", Long.toString(file.size()));
            entity.put("encodingFormat", file.mediaType() == null || file.mediaType().isBlank()
                    ? "application/octet-stream" : file.mediaType());
            entity.put("sha256", file.sha256());
            graph.add(entity);
        }
        root.put("hasPart", parts);
        return Map.of("@context", "https://w3id.org/ro/crate/1.2/context", "@graph", graph);
    }

    private static Map<String, String> ref(String id) { return Map.of("@id", id); }

    private static String description(String path) {
        return switch (path) {
            case "preservation/metadata.json" -> "Metadatos locales y estado editorial del recurso.";
            case "preservation/provenance.json" -> "Eventos de procedencia registrados por el repositorio.";
            case "preservation/manifest-sha256.txt" -> "Huellas SHA-256 registradas al ingreso; no describen necesariamente el contenido actual.";
            default -> "Archivo incluido en el paquete de preservación: " + path;
        };
    }

    /** URI references differ from ZIP entry names: spaces and '%' must be escaped. */
    private static String uriPath(String path) {
        return java.util.Arrays.stream(path.split("/", -1))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }

    public record PackagedFile(String path, long size, String mediaType, String sha256) {}
}
