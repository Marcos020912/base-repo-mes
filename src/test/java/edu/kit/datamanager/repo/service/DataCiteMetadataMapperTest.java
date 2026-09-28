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

        var mapped = new DataCiteMetadataMapper().version(resource, record, URI.create("https://datos.reduniv.edu.cu/datasets/r1"));
        assertEquals("https://datos.reduniv.edu.cu/datasets/r1", mapped.get("url"));
        assertEquals(2026, mapped.get("publicationYear"));
        assertEquals("1.0", mapped.get("version"));
        assertEquals(java.util.Map.of("resourceTypeGeneral", "Dataset"), mapped.get("types"));
        assertFalse(mapped.containsKey("event"));
    }

    @Test
    public void missingPublicationMetadataCannotBeSentAsFindable() {
        assertThrows(IllegalArgumentException.class, () -> new DataCiteMetadataMapper().version(
                mock(DataResource.class), new ScientificRecord("r1"), URI.create("https://datos.reduniv.edu.cu/datasets/r1")));
    }
}
