package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.ScientificQualityService;
import edu.kit.datamanager.repo.web.impl.ScientificRecordController;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScientificRecordControllerTest {
    private ScientificRecordRepository records;
    private ResourceOwnershipRepository owners;
    private IDataResourceDao resources;
    private ScientificQualityService quality;
    private ScientificRecordController controller;

    @Before
    public void setUp() {
        records = mock(ScientificRecordRepository.class);
        owners = mock(ResourceOwnershipRepository.class);
        resources = mock(IDataResourceDao.class);
        quality = mock(ScientificQualityService.class);
        when(resources.findById("r1")).thenReturn(Optional.of(mock(DataResource.class)));
        when(owners.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
        when(records.save(any(ScientificRecord.class))).thenAnswer(call -> call.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author", null));
        when(quality.inspect(any(ScientificRecord.class))).thenReturn(new ScientificQualityService.QualityReport(100, java.util.List.of(), java.util.List.of()));
        controller = new ScientificRecordController(records, owners, resources, quality);
    }

    @After
    public void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    public void draftCanBeEditedAndSubmitted() {
        ScientificRecord updated = controller.update("r1", new ScientificRecordController.UpdateRequest(
                "1.0", "10.1234/version-one", null, "CC-BY-4.0", "OPEN", null, "es", "Física", "datos",
                "0000-0002-1825-0097", "Universidad", null, null, "Métodos reproducibles"));
        assertEquals(PublicationStatus.DRAFT, updated.getStatus());
        when(records.findById("r1")).thenReturn(Optional.of(updated));
        ScientificRecord submitted = controller.submit("r1");
        assertEquals(PublicationStatus.IN_REVIEW, submitted.getStatus());
        assertNotNull(submitted.getSubmittedAt());
    }

    @Test
    public void publishingRequiresRegisteredDoiAttestation() {
        ScientificRecord record = new ScientificRecord("r1");
        record.setStatus(PublicationStatus.IN_REVIEW);
        record.setVersionLabel("1.0"); record.setVersionDoi("10.1234/version-one"); record.setLicenseId("CC-BY-4.0");
        when(records.findById("r1")).thenReturn(Optional.of(record));
        assertThrows(ResponseStatusException.class, () -> controller.publish("r1", new ScientificRecordController.PublicationApproval(false)));
        ScientificRecord published = controller.publish("r1", new ScientificRecordController.PublicationApproval(true));
        assertEquals(PublicationStatus.PUBLISHED, published.getStatus());
        assertNotNull(published.getPublishedAt());
        assertThrows(ResponseStatusException.class, () -> controller.update("r1", new ScientificRecordController.UpdateRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null)));
    }

    @Test
    public void newVersionReferencesOnlyPublishedOwnResource() {
        when(resources.findById("r2")).thenReturn(Optional.of(mock(DataResource.class)));
        when(owners.findById("r2")).thenReturn(Optional.of(new ResourceOwnership("r2", "author")));
        ScientificRecord previous = new ScientificRecord("r1"); previous.setStatus(PublicationStatus.PUBLISHED);
        previous.setConceptualDoi("10.1234/concept");
        when(records.findById("r1")).thenReturn(Optional.of(previous));
        ScientificRecord derived = controller.deriveFrom("r2", "r1");
        assertEquals("r1", derived.getPreviousResourceId());
        assertEquals("10.1234/concept", derived.getConceptualDoi());
        assertEquals(PublicationStatus.DRAFT, derived.getStatus());
    }
}
