package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.DoiRegistration;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificRelation;
import edu.kit.datamanager.repo.repository.DoiRegistrationRepository;
import edu.kit.datamanager.repo.repository.DoiSyncEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.ScientificRelationRepository;
import edu.kit.datamanager.repo.service.DataCiteMetadataMapper;
import edu.kit.datamanager.repo.service.DataCiteService;
import edu.kit.datamanager.repo.service.DoiWorkflowService;
import edu.kit.datamanager.repo.service.ScientificQualityService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DoiWorkflowServiceTest {
    private final DataCiteService datacite = mock(DataCiteService.class);
    private final DataCiteMetadataMapper mapper = mock(DataCiteMetadataMapper.class);
    private final DoiRegistrationRepository registrations = mock(DoiRegistrationRepository.class);
    private final DoiSyncEventRepository syncEvents = mock(DoiSyncEventRepository.class);
    private final ScientificRecordRepository records = mock(ScientificRecordRepository.class);
    private final ScientificRecordEventRepository editorial = mock(ScientificRecordEventRepository.class);
    private final IDataResourceDao resources = mock(IDataResourceDao.class);
    private final ScientificQualityService quality = mock(ScientificQualityService.class);
    private final ScientificRelationRepository relations = mock(ScientificRelationRepository.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final Map<String, DoiRegistration> local = new HashMap<>();
    private final Map<String, String> remote = new HashMap<>();
    private final ScientificRecord science = new ScientificRecord("r1");
    private DoiWorkflowService workflow;

    @Before
    public void setup() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(datacite.isEnabled()).thenReturn(true);
        when(datacite.prefix()).thenReturn("10.1234");
        when(datacite.apiHost()).thenReturn("api.test.datacite.org");
        when(resources.findById("r1")).thenReturn(Optional.of(mock(DataResource.class)));
        when(records.findById("r1")).thenReturn(Optional.of(science));
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));
        when(registrations.findById(anyString())).thenAnswer(call -> Optional.ofNullable(local.get(call.getArgument(0))));
        when(registrations.saveAndFlush(any())).thenAnswer(call -> {
            DoiRegistration registration = call.getArgument(0); local.put(registration.getRegistrationKey(), registration); return registration;
        });
        when(registrations.save(any())).thenAnswer(call -> {
            DoiRegistration registration = call.getArgument(0); local.put(registration.getRegistrationKey(), registration); return registration;
        });
        when(datacite.lookup(anyString())).thenAnswer(call -> {
            String doi = call.getArgument(0); String state = remote.get(doi);
            return state == null ? Optional.empty() : Optional.of(new DataCiteService.DoiResponse(doi, state));
        });
        when(datacite.reserveDraft(anyString())).thenAnswer(call -> {
            String doi = call.getArgument(0); remote.put(doi, "draft"); return new DataCiteService.DoiResponse(doi, "draft");
        });
        when(datacite.publish(anyString(), anyMap())).thenAnswer(call -> {
            String doi = call.getArgument(0); remote.put(doi, "findable"); return new DataCiteService.DoiResponse(doi, "findable");
        });
        when(datacite.updateMetadata(anyString(), anyMap())).thenAnswer(call -> {
            String doi = call.getArgument(0); return new DataCiteService.DoiResponse(doi, "findable");
        });
        when(mapper.version(any(), any(), any())).thenAnswer(call -> Map.of("url", call.getArgument(2).toString()));
        when(quality.inspect(any())).thenReturn(new ScientificQualityService.QualityReport(100, java.util.List.of(), java.util.List.of()));
        when(records.findByConceptualDoiIgnoreCaseAndStatusOrderByPublishedAtDesc(anyString(), eq(PublicationStatus.PUBLISHED)))
                .thenReturn(java.util.List.of());
        when(records.findByConceptualDoiIgnoreCase(anyString())).thenReturn(java.util.List.of());
        when(relations.findByResourceIdOrderByIdAsc(anyString())).thenReturn(java.util.List.of());
        workflow = new DoiWorkflowService(datacite, mapper, registrations, syncEvents, records, editorial,
                resources, quality, relations, manager, "https://datos.reduniv.edu.cu");
    }

    @Test
    public void reserveIsIdempotentAndDoesNotPublish() {
        var first = workflow.reserve("r1");
        var second = workflow.reserve("r1");
        assertEquals(first.versionDoi(), second.versionDoi());
        assertEquals("DRAFT", second.versionState());
        assertEquals("DRAFT", second.conceptualState());
        assertEquals(2, local.size());
        verify(datacite, times(2)).reserveDraft(anyString());
        verify(datacite, never()).publish(anyString(), anyMap());
    }

    @Test
    public void reviewPublishesBothDoisBeforeLocalState() {
        science.setStatus(PublicationStatus.IN_REVIEW);
        ScientificRecord result = workflow.publish("r1", "curator");
        assertEquals(PublicationStatus.PUBLISHED, result.getStatus());
        assertEquals("FINDABLE", local.get("c:r1").getState());
        assertEquals("FINDABLE", local.get("v:r1").getState());
        verify(datacite, times(2)).publish(anyString(), anyMap());
        verify(editorial).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void versionDoiContainsTypedScientificRelations() {
        science.setStatus(PublicationStatus.IN_REVIEW);
        when(relations.findByResourceIdOrderByIdAsc("r1")).thenReturn(java.util.List.of(
                new ScientificRelation("r1", ScientificRelation.Kind.ARTICLE,
                        ScientificRelation.IdentifierType.DOI, ScientificRelation.RelationType.IsSupplementTo,
                        "10.5678/article.42", "Artículo relacionado")));
        workflow.publish("r1", "curator");
        var payloads = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(datacite, times(2)).publish(anyString(), payloads.capture());
        Map<String, Object> version = payloads.getAllValues().stream()
                .filter(value -> ((java.util.List<Map<String, String>>) value.get("relatedIdentifiers")).stream()
                        .anyMatch(item -> "IsVersionOf".equals(item.get("relationType"))))
                .findFirst().orElseThrow();
        assertTrue(((java.util.List<Map<String, String>>) version.get("relatedIdentifiers")).stream()
                .anyMatch(item -> "10.5678/article.42".equals(item.get("relatedIdentifier"))
                        && "DOI".equals(item.get("relatedIdentifierType"))
                        && "IsSupplementTo".equals(item.get("relationType"))));
    }

    @Test
    public void failedSecondPublishCanBeRetriedWithoutNewDoi() {
        science.setStatus(PublicationStatus.IN_REVIEW);
        java.util.concurrent.atomic.AtomicBoolean failVersionOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            String doi = call.getArgument(0);
            if (doi.equals(local.get("v:r1").getDoi()) && failVersionOnce.getAndSet(false)) throw new IllegalStateException("offline");
            remote.put(doi, "findable"); return new DataCiteService.DoiResponse(doi, "findable");
        }).when(datacite).publish(anyString(), anyMap());
        assertThrows(IllegalStateException.class, () -> workflow.publish("r1", "curator"));
        assertEquals(PublicationStatus.IN_REVIEW, science.getStatus());
        String versionDoi = local.get("v:r1").getDoi();
        ScientificRecord retried = workflow.publish("r1", "curator");
        assertEquals(PublicationStatus.PUBLISHED, retried.getStatus());
        assertEquals(versionDoi, retried.getVersionDoi());
        assertEquals(2, local.size());
    }

    @Test
    public void rejectsChangingDataciteEnvironmentForExistingReservation() {
        workflow.reserve("r1");
        when(datacite.apiHost()).thenReturn("api.datacite.org");
        assertThrows(ResponseStatusException.class, () -> workflow.reserve("r1"));
        assertEquals(2, local.size());
    }

    @Test
    public void invalidPublicUrlFailsBeforeCreatingAnyDoi() {
        var invalid = new DoiWorkflowService(datacite, mapper, registrations, syncEvents, records, editorial,
                resources, quality, relations, manager, "http://localhost:8090");
        assertThrows(ResponseStatusException.class, () -> invalid.reserve("r1"));
        verify(datacite, never()).reserveDraft(anyString());
        assertTrue(local.isEmpty());
    }
}
