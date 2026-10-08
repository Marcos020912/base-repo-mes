package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.service.ScientificQualityService;
import edu.kit.datamanager.repo.web.impl.ScientificRecordController;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScientificRecordControllerTest {
    private ScientificRecordRepository records;
    private ResourceOwnershipRepository owners;
    private IDataResourceDao resources;
    private ScientificQualityService quality;
    private ScientificRecordEventRepository events;
    private ScientificRecordController controller;

    @Before
    public void setUp() {
        records = mock(ScientificRecordRepository.class);
        owners = mock(ResourceOwnershipRepository.class);
        resources = mock(IDataResourceDao.class);
        quality = mock(ScientificQualityService.class);
        events = mock(ScientificRecordEventRepository.class);
        when(resources.findById("r1")).thenReturn(Optional.of(mock(DataResource.class)));
        when(owners.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
        when(records.save(any(ScientificRecord.class))).thenAnswer(call -> call.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author", null));
        when(quality.inspect(any(ScientificRecord.class))).thenReturn(new ScientificQualityService.QualityReport(100, java.util.List.of(), java.util.List.of()));
        controller = new ScientificRecordController(records, owners, resources, quality, events);
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
        verify(events, atLeastOnce()).save(any());
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
    public void automatedDoiModeRejectsManualPublicationAndDoiOverride() {
        ReflectionTestUtils.setField(controller, "automatedDoiEnabled", true);
        ScientificRecord record = new ScientificRecord("r1");
        when(records.findById("r1")).thenReturn(Optional.of(record));
        assertThrows(ResponseStatusException.class, () -> controller.update("r1", new ScientificRecordController.UpdateRequest(
                "1.0", "10.1234/manual", null, "CC-BY-4.0", "OPEN", null, "es", "Física", "datos", null,
                "Universidad", null, null, "Métodos")));
        record.setStatus(PublicationStatus.IN_REVIEW);
        assertThrows(ResponseStatusException.class, () -> controller.publish("r1", new ScientificRecordController.PublicationApproval(true)));
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

    @Test
    public void onlyAuthorOrCuratorCanReadInternalHistory() {
        assertNotNull(controller.history("r1"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other", null));
        assertThrows(ResponseStatusException.class, () -> controller.history("r1"));
    }
    private ScientificRecordController.UpdateRequest extended(String summary,String start,String end,String geography,
            java.util.Map<String,edu.kit.datamanager.repo.domain.LocalizedScientificMetadata> translations) {
        return new ScientificRecordController.UpdateRequest("1.0",null,null,"CC-BY-4.0","OPEN",null,"es",null,null,
            null,"Universidad",null,null,"Método",summary,start,end,geography,translations);
    }
    @Test public void structuredMetadataAndTranslationsAreValidatedAndCanonicalized() {
        var input=extended(" Resumen ","2025-01-01","2025-12-31"," Cuba ",java.util.Map.of("en-us",
            new edu.kit.datamanager.repo.domain.LocalizedScientificMetadata(" Translated title "," Summary ")));
        var saved=controller.update("r1",input);
        assertEquals("Resumen",saved.getSummary());assertEquals("Cuba",saved.getGeographicCoverage());
        assertEquals(java.time.LocalDate.of(2025,1,1),saved.getTemporalStart());
        assertEquals("Translated title",saved.getTranslations().get("en-US").getTitle());
    }
    @Test public void invalidCoverageAndDuplicateOrEmptyTranslationsAreRejected() {
        assertThrows(ResponseStatusException.class,()->controller.update("r1",extended("x","2025-12-31","2025-01-01",null,null)));
        assertThrows(ResponseStatusException.class,()->controller.update("r1",extended("x","not-date",null,null,null)));
        assertThrows(ResponseStatusException.class,()->controller.update("r1",extended("x",null,null,null,java.util.Map.of("invalid_language",new edu.kit.datamanager.repo.domain.LocalizedScientificMetadata("x",null)))));
        assertThrows(ResponseStatusException.class,()->controller.update("r1",extended("x",null,null,null,java.util.Map.of("en",new edu.kit.datamanager.repo.domain.LocalizedScientificMetadata(null,null)))));
        assertThrows(ResponseStatusException.class,()->controller.update("r1",extended("x",null,null,null,java.util.Map.of("en-US",new edu.kit.datamanager.repo.domain.LocalizedScientificMetadata("x",null),"en-us",new edu.kit.datamanager.repo.domain.LocalizedScientificMetadata("y",null)))));
    }
    @Test public void omittedNewFieldsPreserveStoredValuesAndExplicitBlankClears() {
        var record=controller.update("r1",extended("Resumen","2025-01-01",null,"Cuba",java.util.Map.of("en",new edu.kit.datamanager.repo.domain.LocalizedScientificMetadata("Title",null))));
        when(records.findById("r1")).thenReturn(Optional.of(record));
        controller.update("r1",extended(null,null,null,null,null));
        assertEquals("Resumen",record.getSummary());assertEquals(1,record.getTranslations().size());
        controller.update("r1",extended("","","","",java.util.Map.of()));
        assertNull(record.getSummary());assertNull(record.getTemporalStart());assertTrue(record.getTranslations().isEmpty());
    }
    @Test public void structuredFieldsCannotBeChangedAfterPublicationOrByOtherAuthor() {
        var record=new ScientificRecord("r1");record.setStatus(PublicationStatus.PUBLISHED);
        when(records.findById("r1")).thenReturn(Optional.of(record));
        assertThrows(ResponseStatusException.class,()->controller.update("r1",extended("changed",null,null,null,null)));
        record.setStatus(PublicationStatus.DRAFT);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other",null));
        assertThrows(ResponseStatusException.class,()->controller.update("r1",extended("changed",null,null,null,null)));
        verify(records,never()).save(any());
    }
}
