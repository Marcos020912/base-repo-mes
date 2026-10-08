package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.DoiRegistration;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificRelation;
import edu.kit.datamanager.repo.domain.ScientificFunding;
import edu.kit.datamanager.repo.domain.ScientificAffiliation;
import edu.kit.datamanager.repo.repository.DoiRegistrationRepository;
import edu.kit.datamanager.repo.repository.DoiSyncEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.ScientificRelationRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificFundingRepository;
import edu.kit.datamanager.repo.repository.ScientificAffiliationRepository;
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
    private final ScientificCreatorRepository creators = mock(ScientificCreatorRepository.class);
    private final ScientificFundingRepository funding = mock(ScientificFundingRepository.class);
    private final ScientificAffiliationRepository affiliations = mock(ScientificAffiliationRepository.class);
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
        when(mapper.version(any(), any(), any(), any(), any(), any())).thenAnswer(call -> Map.of("url", call.getArgument(2).toString()));
        when(quality.inspect(any())).thenReturn(new ScientificQualityService.QualityReport(100, java.util.List.of(), java.util.List.of()));
        when(records.findByConceptualDoiIgnoreCaseAndStatusOrderByPublishedAtDesc(anyString(), eq(PublicationStatus.PUBLISHED)))
                .thenReturn(java.util.List.of());
        when(records.findByConceptualDoiIgnoreCase(anyString())).thenReturn(java.util.List.of());
        when(relations.findByResourceIdOrderByIdAsc(anyString())).thenReturn(java.util.List.of());
        when(creators.findByResourceId(anyString())).thenReturn(java.util.List.of());
        when(funding.findByResourceIdOrderByIdAsc(anyString())).thenReturn(java.util.List.of());
        when(affiliations.findByResourceIdOrderByCreatorIdAscSortOrderAsc(anyString())).thenReturn(java.util.List.of());
        workflow = new DoiWorkflowService(datacite, mapper, registrations, syncEvents, records, editorial,
                resources, quality, relations, creators, funding, affiliations, manager, "https://datos.reduniv.edu.cu");
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
    public void publishingPassesFundingToBothVersionAndConceptualDois() {
        science.setStatus(PublicationStatus.IN_REVIEW);
        var grant = new ScientificFunding("r1", "Agencia", "03yrm5c26", "P-42", "Proyecto");
        when(funding.findByResourceIdOrderByIdAsc("r1")).thenReturn(java.util.List.of(grant));
        workflow.publish("r1", "curator");
        verify(mapper, times(2)).version(any(), any(), any(), any(), eq(java.util.List.of(grant)), any());
    }

    @Test
    public void publishingPassesAllCreatorAffiliationsToDatacite() {
        science.setStatus(PublicationStatus.IN_REVIEW);
        var affiliation = new ScientificAffiliation("r1", 1L, 0, "Universidad", "03yrm5c26");
        when(affiliations.findByResourceIdOrderByCreatorIdAscSortOrderAsc("r1"))
                .thenReturn(java.util.List.of(affiliation));
        workflow.publish("r1", "curator");
        verify(mapper, times(2)).version(any(), any(), any(), any(), any(), eq(java.util.List.of(affiliation)));
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
                resources, quality, relations, creators, funding, affiliations, manager, "http://localhost:8090");
        assertThrows(ResponseStatusException.class, () -> invalid.reserve("r1"));
        verify(datacite, never()).reserveDraft(anyString());
        assertTrue(local.isEmpty());
    }
    @Test public void configuredLandingRefreshRequiresExactConfirmationAndAuditsBoth() {
        workflow.reserve("r1");science.setStatus(PublicationStatus.PUBLISHED);
        var targets=workflow.landingTargets("r1");
        when(datacite.updateUrl(anyString(),any())).thenAnswer(call->new DataCiteService.DoiResponse(call.getArgument(0),"findable"));
        assertEquals(targets,workflow.refreshLandingUrls("r1",targets,"admin"));
        verify(datacite).updateUrl(targets.versionDoi(),java.net.URI.create("https://datos.reduniv.edu.cu/datasets/r1"));
        verify(datacite).updateUrl(targets.conceptualDoi(),java.net.URI.create("https://datos.reduniv.edu.cu/datasets/r1/concept"));
        assertEquals("FINDABLE",local.get("v:r1").getState());
        verify(editorial).save(any());
    }
    @Test public void landingRefreshRejectsArbitraryRedirectWithoutRemoteWrites() {
        workflow.reserve("r1");science.setStatus(PublicationStatus.PUBLISHED);
        var targets=workflow.landingTargets("r1");
        try {workflow.refreshLandingUrls("r1",new DoiWorkflowService.LandingTargets(targets.versionDoi(),"https://evil.example/",targets.conceptualDoi(),targets.conceptualUrl()),"admin");fail();}
        catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}
        verify(datacite,never()).updateUrl(anyString(),any());
    }
    @Test public void landingRefreshRejectsForeignEnvironmentAndManualDois() {
        science.setStatus(PublicationStatus.PUBLISHED);
        try {workflow.landingTargets("r1");fail();}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}
        science.setStatus(PublicationStatus.DRAFT);workflow.reserve("r1");science.setStatus(PublicationStatus.PUBLISHED);
        local.get("v:r1").setApiHost("api.datacite.org");
        try {workflow.landingTargets("r1");fail();}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}
        verify(datacite,never()).updateUrl(anyString(),any());
    }
    @Test public void partialLandingRefreshCanBeRetriedWithoutChangingContent() {
        workflow.reserve("r1");science.setStatus(PublicationStatus.PUBLISHED);
        var targets=workflow.landingTargets("r1");
        when(datacite.updateUrl(eq(targets.versionDoi()),any())).thenReturn(new DataCiteService.DoiResponse(targets.versionDoi(),"findable"));
        when(datacite.updateUrl(eq(targets.conceptualDoi()),any())).thenThrow(new IllegalStateException("network"))
            .thenReturn(new DataCiteService.DoiResponse(targets.conceptualDoi(),"findable"));
        try {workflow.refreshLandingUrls("r1",targets,"admin");fail();}catch(IllegalStateException expected){}
        assertEquals("ERROR",local.get("c:r1").getState());assertEquals(PublicationStatus.PUBLISHED,science.getStatus());
        workflow.refreshLandingUrls("r1",targets,"admin");assertEquals("FINDABLE",local.get("c:r1").getState());
    }
    private ScientificRecord predecessor() {
        var previous=new ScientificRecord("r0");
        when(resources.findById("r0")).thenReturn(Optional.of(mock(DataResource.class)));
        when(records.findById("r0")).thenReturn(Optional.of(previous));
        workflow.reserve("r0");previous.setStatus(PublicationStatus.PUBLISHED);
        remote.put(previous.getVersionDoi(),"findable");remote.put(previous.getConceptualDoi(),"findable");
        science.setPreviousResourceId("r0");science.setStatus(PublicationStatus.IN_REVIEW);
        return previous;
    }
    @Test public void publishSynchronizesInverseRelationWithoutOverwritingOtherMetadata() {
        var previous=predecessor();
        var sibling=new ScientificRecord("sibling");sibling.setStatus(PublicationStatus.PUBLISHED);sibling.setVersionDoi("10.1234/sibling");
        when(records.findByPreviousResourceId("r0")).thenReturn(java.util.List.of(sibling));
        workflow.publish("r1","curator");
        var capture=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(datacite).updateMetadata(eq(previous.getVersionDoi()),capture.capture());
        assertEquals(java.util.Set.of("relatedIdentifiers"),capture.getValue().keySet());
        var identifiers=(java.util.List<Map<String,String>>)capture.getValue().get("relatedIdentifiers");
        assertTrue(identifiers.stream().anyMatch(item->"IsPreviousVersionOf".equals(item.get("relationType")) && science.getVersionDoi().equals(item.get("relatedIdentifier"))));
        assertTrue(identifiers.stream().anyMatch(item->"10.1234/sibling".equals(item.get("relatedIdentifier"))));
        assertTrue(identifiers.stream().anyMatch(item->"IsVersionOf".equals(item.get("relationType"))));
        assertEquals(PublicationStatus.PUBLISHED,science.getStatus());
    }
    @Test public void inverseRelationFailureLeavesReviewAndCanBeRetried() {
        var previous=predecessor();
        when(datacite.updateMetadata(eq(previous.getVersionDoi()),anyMap()))
            .thenThrow(new IllegalStateException("network"))
            .thenReturn(new DataCiteService.DoiResponse(previous.getVersionDoi(),"findable"));
        try {workflow.publish("r1","curator");fail();}catch(IllegalStateException expected){}
        assertEquals(PublicationStatus.IN_REVIEW,science.getStatus());
        workflow.publish("r1","curator");assertEquals(PublicationStatus.PUBLISHED,science.getStatus());
        verify(datacite,times(2)).updateMetadata(eq(previous.getVersionDoi()),anyMap());
    }
}
