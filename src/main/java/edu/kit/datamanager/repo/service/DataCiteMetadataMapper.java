package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificFunding;
import edu.kit.datamanager.repo.domain.ScientificAffiliation;
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
        return version(resource, science, landingPage, creatorDetails, fundingDetails, List.of());
    }

    public Map<String, Object> version(DataResource resource, ScientificRecord science, URI landingPage,
            List<ScientificCreator> creatorDetails, List<ScientificFunding> fundingDetails,
            List<ScientificAffiliation> affiliationDetails) {
        if (!"https".equalsIgnoreCase(landingPage.getScheme()) || landingPage.getHost() == null)
            throw new IllegalArgumentException("La landing page del DOI debe usar HTTPS.");
        String title = resource.getTitles() == null ? null : resource.getTitles().stream()
                .map(item -> item.getValue()).filter(DataCiteMetadataMapper::hasText).findFirst().orElse(null);
        Map<Long, ScientificCreator> identities = new java.util.HashMap<>();
        if (creatorDetails != null) creatorDetails.forEach(item -> identities.put(item.getCreatorId(), item));
        Map<Long, List<ScientificAffiliation>> organizations = new java.util.HashMap<>();
        if (affiliationDetails != null) affiliationDetails.forEach(item ->
                organizations.computeIfAbsent(item.getCreatorId(), unused -> new ArrayList<>()).add(item));
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
                    }
                    List<ScientificAffiliation> affiliations = organizations.getOrDefault(item.getId(), List.of());
                    List<Map<String, Object>> mappedAffiliations = new ArrayList<>();
                    if (!affiliations.isEmpty()) {
                        affiliations.stream().sorted(java.util.Comparator.comparingInt(ScientificAffiliation::getSortOrder))
                                .forEach(affiliation -> mappedAffiliations.add(affiliation(
                                        affiliation.getInstitution(), affiliation.getRor())));
                    } else if (identity != null && hasText(identity.getInstitution())) {
                        mappedAffiliations.add(affiliation(identity.getInstitution(), identity.getRor()));
                    }
                    if (!mappedAffiliations.isEmpty()) creator.put("affiliation", mappedAffiliations);
                    return creator;
                }).toList();
        String publisher = resource.getPublisher();
        String year = resource.getPublicationYear();
        if (!hasText(title) || creators.isEmpty() || !hasText(publisher) || year == null || !year.matches("\\d{4}"))
            throw new IllegalArgumentException("Faltan título, autores, editorial o año de publicación para DataCite.");

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("creators", creators);
        List<Map<String,String>> titles=new ArrayList<>();
        titles.add(Map.of("title",title));
        science.getTranslations().forEach((language,value)->{
            if(hasText(value.getTitle()))titles.add(Map.of("title",value.getTitle(),"lang",language,"titleType","TranslatedTitle"));
        });
        attributes.put("titles",titles);
        List<Map<String,String>> descriptions=new ArrayList<>();
        if(hasText(science.getSummary()))descriptions.add(Map.of("description",science.getSummary(),"descriptionType","Abstract"));
        if(hasText(science.getMethodology()))descriptions.add(Map.of("description",science.getMethodology(),"descriptionType","Methods"));
        science.getTranslations().forEach((language,value)->{
            if(hasText(value.getSummary()))descriptions.add(Map.of("description",value.getSummary(),"descriptionType","Abstract","lang",language));
        });
        if(!descriptions.isEmpty())attributes.put("descriptions",descriptions);
        if(hasText(science.getGeographicCoverage()))attributes.put("geoLocations",List.of(Map.of("geoLocationPlace",science.getGeographicCoverage())));
        if(science.getTemporalStart()!=null || science.getTemporalEnd()!=null) {
            String period=science.getTemporalStart()!=null && science.getTemporalEnd()!=null?science.getTemporalStart()+"/"+science.getTemporalEnd():
                (science.getTemporalStart()!=null?science.getTemporalStart():science.getTemporalEnd()).toString();
            attributes.put("dates",List.of(Map.of("date",period,"dateType","Collected","dateInformation",science.getTemporalStart()==null?"Fin de cobertura temporal declarado":science.getTemporalEnd()==null?"Inicio de cobertura temporal declarado":"Cobertura temporal declarada por el depositante")));
        }
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

    private static Map<String, Object> affiliation(String institution, String rorValue) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", institution);
        if (hasText(rorValue)) {
            String ror = rorValue.replaceFirst("^https://ror.org/", "");
            value.put("affiliationIdentifier", "https://ror.org/" + ror);
            value.put("affiliationIdentifierScheme", "ROR");
            value.put("schemeUri", "https://ror.org/");
        }
        return value;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
