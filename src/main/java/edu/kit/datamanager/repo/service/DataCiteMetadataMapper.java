package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Minimum publication metadata for a version DOI; no draft identifier is exposed publicly. */
@Component
public class DataCiteMetadataMapper {
    public Map<String, Object> version(DataResource resource, ScientificRecord science, URI landingPage) {
        if (!"https".equalsIgnoreCase(landingPage.getScheme()) || landingPage.getHost() == null)
            throw new IllegalArgumentException("La landing page del DOI debe usar HTTPS.");
        String title = resource.getTitles() == null ? null : resource.getTitles().stream()
                .map(item -> item.getValue()).filter(DataCiteMetadataMapper::hasText).findFirst().orElse(null);
        List<Map<String, String>> creators = resource.getCreators() == null ? List.of() : resource.getCreators().stream()
                .map(item -> String.join(" ", item.getGivenName() == null ? "" : item.getGivenName(),
                        item.getFamilyName() == null ? "" : item.getFamilyName()).trim())
                .filter(DataCiteMetadataMapper::hasText).map(name -> Map.of("name", name)).toList();
        String publisher = resource.getPublisher();
        String year = resource.getPublicationYear();
        if (!hasText(title) || creators.isEmpty() || !hasText(publisher) || year == null || !year.matches("\\d{4}"))
            throw new IllegalArgumentException("Faltan título, autores, editorial o año de publicación para DataCite.");

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("creators", creators);
        attributes.put("titles", List.of(Map.of("title", title)));
        attributes.put("publisher", publisher);
        attributes.put("publicationYear", Integer.parseInt(year));
        attributes.put("types", Map.of("resourceTypeGeneral", type(resource)));
        attributes.put("url", landingPage.toString());
        if (hasText(science.getVersionLabel())) attributes.put("version", science.getVersionLabel());
        return attributes;
    }

    private static String type(DataResource resource) {
        if (resource.getResourceType() == null || resource.getResourceType().getTypeGeneral() == null) return "Dataset";
        return switch (resource.getResourceType().getTypeGeneral()) {
            case IMAGE -> "Image";
            case TEXT -> "Text";
            case AUDIOVISUAL -> "Audiovisual";
            case SOUND -> "Sound";
            case DATASET -> "Dataset";
            default -> "Other";
        };
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
