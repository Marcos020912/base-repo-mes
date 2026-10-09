package edu.kit.datamanager.repo.test.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.Title;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.web.impl.ScientificCitationController;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScientificCitationControllerTest {
    @Test public void exportsAdditionalStylesOnlyForPublishedVersion() throws Exception {
        IDataResourceDao resources = mock(IDataResourceDao.class);
        ScientificRecordRepository records = mock(ScientificRecordRepository.class);
        DataResource resource = mock(DataResource.class);
        Agent author = mock(Agent.class);
        Title title = mock(Title.class);
        when(author.getGivenName()).thenReturn("Ana María"); when(author.getFamilyName()).thenReturn("Pérez");
        when(title.getValue()).thenReturn("Datos marinos");
        when(resource.getCreators()).thenReturn(Set.of(author)); when(resource.getTitles()).thenReturn(Set.of(title));
        when(resource.getPublisher()).thenReturn("RedUniv"); when(resource.getPublicationYear()).thenReturn("2026");
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        ScientificRecord science = new ScientificRecord("r1"); science.setVersionDoi("10.1234/mar"); science.setVersionLabel("2.0");
        when(records.findById("r1")).thenReturn(Optional.of(science));
        ScientificCitationController controller = new ScientificCitationController(resources, records, new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> controller.export("r1", "vancouver"));
        science.setStatus(PublicationStatus.PUBLISHED);
        assertTrue(controller.export("r1", "vancouver").getBody().contains("Pérez AM. Datos marinos"));
        assertTrue(controller.export("r1", "chicago").getBody().contains("\"Datos marinos.\""));
        assertTrue(controller.export("r1", "ieee").getBody().contains("A.M. Pérez"));
        assertThrows(ResponseStatusException.class, () -> controller.export("r1", "unknown"));
    }
    @Test public void exportsPreserveSpecialCharactersAndLiteralOrganizations() throws Exception {
        IDataResourceDao resources = mock(IDataResourceDao.class);
        ScientificRecordRepository records = mock(ScientificRecordRepository.class);
        DataResource resource = mock(DataResource.class);
        Agent person = mock(Agent.class), organization = mock(Agent.class);
        when(person.getGivenName()).thenReturn("Ana María"); when(person.getFamilyName()).thenReturn("Pérez");
        when(organization.getGivenName()).thenReturn("Research and Development");
        Title title = mock(Title.class); when(title.getValue()).thenReturn("Datos {Cuba} 50% & x_y #1 $2 ~ ^ \\ruta");
        when(resource.getCreators()).thenReturn(new java.util.LinkedHashSet<>(java.util.List.of(person, organization)));
        when(resource.getTitles()).thenReturn(Set.of(title));
        when(resource.getPublisher()).thenReturn("RedUniv"); when(resource.getPublicationYear()).thenReturn("2026");
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        ScientificRecord science = new ScientificRecord("r1"); science.setStatus(PublicationStatus.PUBLISHED);
        science.setVersionDoi("10.1234/exact-v2"); science.setVersionLabel("2.0");
        when(records.findById("r1")).thenReturn(Optional.of(science));
        ScientificCitationController controller = new ScientificCitationController(resources, records, new ObjectMapper());
        String bib = controller.export("r1", "bibtex").getBody();
        assertTrue(bib.contains("Pérez, Ana María and {Research and Development}"));
        assertTrue(bib.contains("\\{Cuba\\}")); assertTrue(bib.contains("50\\% \\& x\\_y \\#1 \\$2"));
        assertTrue(bib.contains("\\textbackslash{}ruta"));
        com.fasterxml.jackson.databind.JsonNode csl = new ObjectMapper().readTree(controller.export("r1", "csl-json").getBody());
        assertEquals(title.getValue(), csl.get("title").asText());
        assertEquals("10.1234/exact-v2", csl.get("DOI").asText());
        assertEquals("2.0", csl.get("version").asText());
        assertEquals(2026, csl.get("issued").get("date-parts").get(0).get(0).asInt());
        assertEquals(2, csl.get("author").size());
        assertEquals("Research and Development", csl.get("author").get(1).get("literal").asText());
        assertEquals("Pérez", csl.get("author").get(0).get("family").asText());
        for (String format : java.util.List.of("apa", "vancouver", "chicago", "ieee")) {
            var response = controller.export("r1", format);
            assertTrue(format, response.getBody().contains("Research and Development"));
            assertEquals(java.nio.charset.StandardCharsets.UTF_8, response.getHeaders().getContentType().getCharset());
        }
        when(person.getGivenName()).thenReturn("𐐨na İpek");
        assertTrue(controller.export("r1", "vancouver").getBody().contains("Pérez 𐐀İ"));
        assertTrue(controller.export("r1", "ieee").getBody().contains("𐐀.İ. Pérez"));
        when(person.getGivenName()).thenReturn("Ana María");
        String ris = controller.export("r1", "ris").getBody();
        assertTrue(ris.contains("AU  - Pérez, Ana María\n"));
        assertTrue(ris.contains("TI  - " + title.getValue() + "\n"));
        assertTrue(ris.contains("DO  - 10.1234/exact-v2\nET  - 2.0\nER  - "));
        // Artifacts from the actual exporter, consumed by independent Pybtex/Rispy checks.
        var fixture = java.nio.file.Path.of("build", "citation-fixtures");
        java.nio.file.Files.createDirectories(fixture);
        for (String format : java.util.List.of("bibtex", "ris", "csl-json"))
            java.nio.file.Files.writeString(fixture.resolve("citation." + format), controller.export("r1", format).getBody(),
                    java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(fixture.resolve("expected.json"), new ObjectMapper().writeValueAsString(
                java.util.Map.of("title", title.getValue(), "publisher", "RedUniv", "year", "2026",
                        "doi", "10.1234/exact-v2", "version", "2.0", "personGiven", "Ana María",
                        "personFamily", "Pérez", "organization", "Research and Development")),
                java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test public void allFormatsRejectEveryUnpublishedStateWithoutReadingResource() {
        IDataResourceDao resources=mock(IDataResourceDao.class);
        ScientificRecordRepository records=mock(ScientificRecordRepository.class);
        ScientificRecord science=new ScientificRecord("r1");
        when(records.findById("r1")).thenReturn(Optional.of(science));
        ScientificCitationController controller=new ScientificCitationController(resources,records,new ObjectMapper());
        for(PublicationStatus state:PublicationStatus.values()) {
            if(state==PublicationStatus.PUBLISHED) continue;
            science.setStatus(state);
            for(String format:java.util.List.of("apa","vancouver","chicago","ieee","bibtex","ris","csl-json"))
                assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.export("r1",format)).getStatusCode().value());
        }
        verifyNoInteractions(resources);
    }
}
