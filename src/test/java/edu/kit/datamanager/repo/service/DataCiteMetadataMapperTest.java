package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ResourceType;
import edu.kit.datamanager.repo.domain.ScientificRecord;
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
}
