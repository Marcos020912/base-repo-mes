package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificFunding;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Minimum publication metadata for a version DOI; no draft identifier is exposed publicly. */
@Component
public class DataCiteMetadataMapper {
    public Map<String, Object> version(DataResource resource, ScientificRecord science, URI landingPage) {
        return version(resource, science, landingPage, List.of());
    }

    public Map<String, Object> version(DataResource resource, ScientificRecord science, URI landingPage,
            List<ScientificCreator> creatorDetails) {
        return version(resource, science, landingPage, creatorDetails, List.of());
    }

    public Map<String, Object> version(DataResource resource, ScientificRecord science, URI landingPage,
            List<ScientificCreator> creatorDetails, List<ScientificFunding> fundingDetails) {
        if (!"https".equalsIgnoreCase(landingPage.getScheme()) || landingPage.getHost() == null)
            throw new IllegalArgumentException("La landing page del DOI debe usar HTTPS.");
        String title = resource.getTitles() == null ? null : resource.getTitles().stream()
                .map(item -> item.getValue()).filter(DataCiteMetadataMapper::hasText).findFirst().orElse(null);
        Map<Long, ScientificCreator> identities = new java.util.HashMap<>();
        if (creatorDetails != null) creatorDetails.forEach(item -> identities.put(item.getCreatorId(), item));
        List<Map<String, Object>> creators = resource.getCreators() == null ? List.of() : resource.getCreators().stream()
                .filter(item -> hasText(item.getGivenName()) || hasText(item.getFamilyName())).map(item -> {
                    String name = String.join(" ", item.getGivenName() == null ? "" : item.getGivenName(),
                            item.getFamilyName() == null ? "" : item.getFamilyName()).trim();
                    Map<String, Object> creator = new LinkedHashMap<>();
                    creator.put("name", name);
                    ScientificCreator identity = identities.get(item.getId());
                    if (identity != null) {
                        if (hasText(identity.getOrcid())) {
                            String orcid = identity.getOrcid().replaceFirst("^https://orcid.org/", "");
                            creator.put("nameIdentifiers", List.of(Map.of("nameIdentifier", "https://orcid.org/" + orcid,
                                    "nameIdentifierScheme", "ORCID", "schemeUri", "https://orcid.org/")));
                        }
                        if (hasText(identity.getInstitution())) {
                            Map<String, Object> affiliation = new LinkedHashMap<>();
                            affiliation.put("name", identity.getInstitution());
                            if (hasText(identity.getRor())) {
                                String ror = identity.getRor().replaceFirst("^https://ror.org/", "");
                                affiliation.put("affiliationIdentifier", "https://ror.org/" + ror);
                                affiliation.put("affiliationIdentifierScheme", "ROR");
                                affiliation.put("schemeUri", "https://ror.org/");
                            }
                            creator.put("affiliation", List.of(affiliation));
                        }
                    }
                    return creator;
                }).toList();
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
        // A single ORCID field cannot be attributed safely when there are multiple creators.
        if (creators.size() == 1 && (creatorDetails == null || creatorDetails.isEmpty()) && hasText(science.getOrcid())) {
            String orcid = science.getOrcid().replaceFirst("^https://orcid.org/", "");
            creators.get(0).put("nameIdentifiers", List.of(Map.of("nameIdentifier", "https://orcid.org/" + orcid,
                    "nameIdentifierScheme", "ORCID", "schemeUri", "https://orcid.org/")));
        }
        if (hasText(science.getInstitution())) {
            Map<String, Object> institution = new LinkedHashMap<>();
            institution.put("name", science.getInstitution());
            institution.put("nameType", "Organizational");
            institution.put("contributorType", "HostingInstitution");
            if (hasText(science.getRor())) {
                String ror = science.getRor().replaceFirst("^https://ror.org/", "");
                institution.put("nameIdentifiers", List.of(Map.of("nameIdentifier", "https://ror.org/" + ror,
                        "nameIdentifierScheme", "ROR", "schemeUri", "https://ror.org/")));
            }
            attributes.put("contributors", List.of(institution));
        }
        if (hasText(science.getLicenseId())) attributes.put("rightsList", List.of(Map.of("rights", science.getLicenseId())));
        List<Map<String, String>> subjects = new ArrayList<>();
        if (hasText(science.getDiscipline())) subjects.add(Map.of("subject", science.getDiscipline().trim()));
        if (hasText(science.getKeywords())) for (String keyword : science.getKeywords().split(",")) {
            if (hasText(keyword)) subjects.add(Map.of("subject", keyword.trim()));
        }
        if (!subjects.isEmpty()) attributes.put("subjects", subjects);
        if (fundingDetails != null && !fundingDetails.isEmpty()) {
            attributes.put("fundingReferences", fundingDetails.stream().map(item -> {
                Map<String, Object> reference = new LinkedHashMap<>();
                reference.put("funderName", item.getFunderName());
                if (hasText(item.getFunderRor())) {
                    String ror = item.getFunderRor().replaceFirst("^https://ror.org/", "");
                    reference.put("funderIdentifier", "https://ror.org/" + ror);
                    reference.put("funderIdentifierType", "ROR");
                    reference.put("schemeUri", "https://ror.org/");
                }
                if (hasText(item.getAwardNumber())) reference.put("awardNumber", item.getAwardNumber());
                if (hasText(item.getAwardTitle())) reference.put("awardTitle", item.getAwardTitle());
                return reference;
            }).toList());
        }
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
