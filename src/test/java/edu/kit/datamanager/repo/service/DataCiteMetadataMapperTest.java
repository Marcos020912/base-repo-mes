package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ResourceType;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificFunding;
import edu.kit.datamanager.repo.domain.ScientificAffiliation;
import edu.kit.datamanager.repo.domain.Title;
import java.net.URI;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DataCiteMetadataMapperTest {
    @Test
    public void mapsVersionRequiredFieldsToLandingPage() {
        DataResource resource = mock(DataResource.class);
        Title title = mock(Title.class);
        Agent author = mock(Agent.class);
        ResourceType type = mock(ResourceType.class);
        when(title.getValue()).thenReturn("Datos marinos");
        when(author.getGivenName()).thenReturn("Ana");
        when(author.getFamilyName()).thenReturn("Pérez");
        when(type.getTypeGeneral()).thenReturn(ResourceType.TYPE_GENERAL.DATASET);
        when(resource.getTitles()).thenReturn(Set.of(title));
        when(resource.getCreators()).thenReturn(Set.of(author));
        when(resource.getResourceType()).thenReturn(type);
        when(resource.getPublisher()).thenReturn("RedUniv");
        when(resource.getPublicationYear()).thenReturn("2026");
        ScientificRecord record = new ScientificRecord("r1");
        record.setVersionLabel("1.0");
        record.setOrcid("0000-0003-2804-688X");
        record.setInstitution("RedUniv");
        record.setRor("03yrm5c26");
        record.setLicenseId("CC-BY-4.0");
        record.setDiscipline("Ciencias marinas");
        record.setKeywords("océano, coral");

        var mapped = new DataCiteMetadataMapper().version(resource, record, URI.create("https://datos.reduniv.edu.cu/datasets/r1"));
        assertEquals("https://datos.reduniv.edu.cu/datasets/r1", mapped.get("url"));
        assertEquals(2026, mapped.get("publicationYear"));
        assertEquals("1.0", mapped.get("version"));
        assertEquals(java.util.Map.of("resourceTypeGeneral", "Dataset"), mapped.get("types"));
        var creators = (java.util.List<java.util.Map<String, Object>>) mapped.get("creators");
        assertEquals("https://orcid.org/0000-0003-2804-688X", ((java.util.List<java.util.Map<String, String>>) creators.get(0).get("nameIdentifiers")).get(0).get("nameIdentifier"));
        var contributors = (java.util.List<java.util.Map<String, Object>>) mapped.get("contributors");
        var identifiers = (java.util.List<java.util.Map<String, String>>) contributors.get(0).get("nameIdentifiers");
        assertEquals("https://ror.org/03yrm5c26", identifiers.get(0).get("nameIdentifier"));
        assertEquals(java.util.List.of(java.util.Map.of("rights", "CC-BY-4.0")), mapped.get("rightsList"));
        assertEquals(3, ((java.util.List<?>) mapped.get("subjects")).size());
        assertFalse(mapped.containsKey("event"));
    }

    @Test
    public void missingPublicationMetadataCannotBeSentAsFindable() {
        assertThrows(IllegalArgumentException.class, () -> new DataCiteMetadataMapper().version(
                mock(DataResource.class), new ScientificRecord("r1"), URI.create("https://datos.reduniv.edu.cu/datasets/r1")));
    }

    @Test
    public void singleOrcidIsNotAssignedArbitrarilyAmongSeveralAuthors() {
        DataResource resource = mock(DataResource.class);
        Title title = mock(Title.class);
        Agent first = mock(Agent.class);
        Agent second = mock(Agent.class);
        when(title.getValue()).thenReturn("Datos");
        when(first.getGivenName()).thenReturn("Ana");
        when(second.getGivenName()).thenReturn("Luis");
        when(resource.getTitles()).thenReturn(Set.of(title));
        when(resource.getCreators()).thenReturn(Set.of(first, second));
        when(resource.getPublisher()).thenReturn("RedUniv");
        when(resource.getPublicationYear()).thenReturn("2026");
        ScientificRecord record = new ScientificRecord("r1");
        record.setOrcid("0000-0003-2804-688X");
        var mapped = new DataCiteMetadataMapper().version(resource, record, URI.create("https://datos.reduniv.edu.cu/datasets/r1"));
        var creators = (java.util.List<java.util.Map<String, Object>>) mapped.get("creators");
        assertEquals(2, creators.size());
        assertTrue(creators.stream().noneMatch(creator -> creator.containsKey("nameIdentifiers")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mapsEachCreatorsOwnOrcidAndRorAffiliation() {
        DataResource resource = mock(DataResource.class);
        Title title = mock(Title.class);
        Agent first = mock(Agent.class);
        Agent second = mock(Agent.class);
        when(title.getValue()).thenReturn("Datos");
        when(first.getId()).thenReturn(1L);
        when(first.getGivenName()).thenReturn("Ana");
        when(second.getId()).thenReturn(2L);
        when(second.getGivenName()).thenReturn("Luis");
        when(resource.getTitles()).thenReturn(Set.of(title));
        when(resource.getCreators()).thenReturn(Set.of(first, second));
        when(resource.getPublisher()).thenReturn("RedUniv");
        when(resource.getPublicationYear()).thenReturn("2026");
        var mapped = new DataCiteMetadataMapper().version(resource, new ScientificRecord("r1"),
                URI.create("https://datos.reduniv.edu.cu/datasets/r1"), java.util.List.of(
                        new ScientificCreator("r1", 1L, "0000-0003-2804-688X", "Universidad A", "03yrm5c26"),
                        new ScientificCreator("r1", 2L, "0000-0001-5109-3700", "Universidad B", null)));
        var creators = (java.util.List<java.util.Map<String, Object>>) mapped.get("creators");
        assertEquals(2, creators.size());
        var ana = creators.stream().filter(item -> "Ana".equals(item.get("name"))).findFirst().orElseThrow();
        var luis = creators.stream().filter(item -> "Luis".equals(item.get("name"))).findFirst().orElseThrow();
        assertEquals("https://orcid.org/0000-0003-2804-688X",
                ((java.util.List<java.util.Map<String, String>>) ana.get("nameIdentifiers")).get(0).get("nameIdentifier"));
        assertEquals("https://ror.org/03yrm5c26",
                ((java.util.List<java.util.Map<String, String>>) ana.get("affiliation")).get(0).get("affiliationIdentifier"));
        assertEquals("Universidad B", ((java.util.List<java.util.Map<String, String>>) luis.get("affiliation")).get(0).get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mapsFundingReferencesWithoutInventingIdentifiers() {
        DataResource resource = mock(DataResource.class);
        Title title = mock(Title.class);
        Agent author = mock(Agent.class);
        when(title.getValue()).thenReturn("Datos financiados");
        when(author.getGivenName()).thenReturn("Ana");
        when(resource.getTitles()).thenReturn(Set.of(title));
        when(resource.getCreators()).thenReturn(Set.of(author));
        when(resource.getPublisher()).thenReturn("RedUniv");
        when(resource.getPublicationYear()).thenReturn("2026");
        var mapped = new DataCiteMetadataMapper().version(resource, new ScientificRecord("r1"),
                URI.create("https://datos.reduniv.edu.cu/datasets/r1"), java.util.List.of(),
                java.util.List.of(new ScientificFunding("r1", "Agencia A", "03yrm5c26", "P-42", "Océano"),
                        new ScientificFunding("r1", "Agencia B", null, null, null)));
        var refs = (java.util.List<java.util.Map<String, Object>>) mapped.get("fundingReferences");
        assertEquals(2, refs.size());
        assertEquals("https://ror.org/03yrm5c26", refs.get(0).get("funderIdentifier"));
        assertEquals("ROR", refs.get(0).get("funderIdentifierType"));
        assertEquals("P-42", refs.get(0).get("awardNumber"));
        assertEquals("Océano", refs.get(0).get("awardTitle"));
        assertFalse(refs.get(1).containsKey("funderIdentifier"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void mapsMultipleAffiliationsToTheCorrectCreator() {
        DataResource resource = mock(DataResource.class);
        Title title = mock(Title.class);
        Agent ana = mock(Agent.class);
        Agent luis = mock(Agent.class);
        when(title.getValue()).thenReturn("Datos");
        when(ana.getId()).thenReturn(1L); when(ana.getGivenName()).thenReturn("Ana");
        when(luis.getId()).thenReturn(2L); when(luis.getGivenName()).thenReturn("Luis");
        when(resource.getTitles()).thenReturn(Set.of(title));
        when(resource.getCreators()).thenReturn(Set.of(ana, luis));
        when(resource.getPublisher()).thenReturn("RedUniv");
        when(resource.getPublicationYear()).thenReturn("2026");
        var mapped = new DataCiteMetadataMapper().version(resource, new ScientificRecord("r1"),
                URI.create("https://datos.reduniv.edu.cu/datasets/r1"),
                java.util.List.of(new ScientificCreator("r1", 1L, null, "Legado", null)), java.util.List.of(),
                java.util.List.of(new ScientificAffiliation("r1", 1L, 1, "Instituto B", null),
                        new ScientificAffiliation("r1", 1L, 0, "Universidad A", "03yrm5c26")));
        var creators = (java.util.List<java.util.Map<String, Object>>) mapped.get("creators");
        var first = creators.stream().filter(item -> "Ana".equals(item.get("name"))).findFirst().orElseThrow();
        var second = creators.stream().filter(item -> "Luis".equals(item.get("name"))).findFirst().orElseThrow();
        var affiliations = (java.util.List<java.util.Map<String, Object>>) first.get("affiliation");
        assertEquals(2, affiliations.size());
        assertEquals("Universidad A", affiliations.get(0).get("name"));
        assertEquals("https://ror.org/03yrm5c26", affiliations.get(0).get("affiliationIdentifier"));
        assertEquals("Instituto B", affiliations.get(1).get("name"));
        assertFalse(second.containsKey("affiliation"));
    }
}
