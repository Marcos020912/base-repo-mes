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
}
