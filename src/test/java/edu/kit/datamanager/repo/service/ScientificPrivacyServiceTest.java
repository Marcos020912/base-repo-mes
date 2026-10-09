package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import java.util.*;
import org.junit.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ScientificPrivacyServiceTest {
    private final ScientificPrivacyAssessmentRepository assessments=mock(ScientificPrivacyAssessmentRepository.class);
    private final ScientificRecordRepository records=mock(ScientificRecordRepository.class);
    private final IDataResourceDao resources=mock(IDataResourceDao.class);
    private final ResourceOwnershipRepository owners=mock(ResourceOwnershipRepository.class);
    private final ScientificRecordEventRepository events=mock(ScientificRecordEventRepository.class);
    private final Map<String,ScientificPrivacyAssessment> stored=new HashMap<>();
    private final ScientificRecord science=new ScientificRecord("r1");
    private ScientificPrivacyService privacy;
    private void actor(String name,String role) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    @Before public void setup() {
        actor("author","USER");when(resources.existsById("r1")).thenReturn(true);
        when(records.findById("r1")).thenReturn(Optional.of(science));
        when(owners.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1","author")));
        when(assessments.findById(anyString())).thenAnswer(call->Optional.ofNullable(stored.get(call.getArgument(0))));
        when(assessments.saveAndFlush(any())).thenAnswer(call->{ScientificPrivacyAssessment a=call.getArgument(0);if(stored.containsKey(a.getResourceId()))a.setRevision(a.getRevision()+1);stored.put(a.getResourceId(),a);return a;});
        privacy=new ScientificPrivacyService(assessments,records,resources,owners,events,false,org.mockito.Mockito.mock(ScientificResourceWriteLock.class));
    }
    @After public void cleanup() {SecurityContextHolder.clearContext();}
    @Test public void draftDeclarationIsAuthorOnlyAndPrivateToOthers() {
        var a=privacy.save("r1",ScientificPrivacyAssessment.Classification.PERSONAL,"Protection measures",null);
        assertEquals("Protection measures",privacy.read("r1").assessmentNote());
        actor("other","USER");assertThrows(ResponseStatusException.class,()->privacy.read("r1"));
        actor("admin","ADMINISTRATOR");assertThrows(ResponseStatusException.class,()->privacy.save("r1",ScientificPrivacyAssessment.Classification.NONE,null,a.revision()));
        assertThrows(ResponseStatusException.class,()->privacy.read("r1"));
        science.setStatus(PublicationStatus.IN_REVIEW);assertEquals(a.classification(),privacy.read("r1").classification());
    }
    @Test public void sensitiveDataNeedsRestrictedAccessAndCuratorialApproval() {
        var a=privacy.save("r1",ScientificPrivacyAssessment.Classification.CONFIDENTIAL,"Protection",null);
        assertThrows(ResponseStatusException.class,()->privacy.requireSubmissionAllowed("r1","OPEN"));
        assertThrows(ResponseStatusException.class,()->privacy.requireSubmissionAllowed("r1","EMBARGOED"));
        science.setAccessLevel("RESTRICTED");privacy.requireSubmissionAllowed("r1","RESTRICTED");
        assertThrows(ResponseStatusException.class,()->privacy.requirePublicationAllowed("r1","RESTRICTED"));
        science.setStatus(PublicationStatus.IN_REVIEW);
        assertThrows(ResponseStatusException.class,()->privacy.review("r1",true,"Checked",a.revision()));
        actor("curator","CURATOR");var approved=privacy.review("r1",true,"Checked",a.revision());
        assertEquals("APPROVED",approved.reviewState());assertEquals("curator",approved.reviewedBy());
        privacy.requirePublicationAllowed("r1","RESTRICTED");
        assertThrows(ResponseStatusException.class,()->privacy.requirePublicationAllowed("r1","OPEN"));
    }
    @Test public void staleRevisionsAndInvalidDeclarationsAreRejected() {
        assertThrows(ResponseStatusException.class,()->privacy.save("r1",ScientificPrivacyAssessment.Classification.PERSONAL,null,null));
        var a=privacy.save("r1",ScientificPrivacyAssessment.Classification.NONE,null,null);
        assertThrows(ResponseStatusException.class,()->privacy.save("r1",ScientificPrivacyAssessment.Classification.NONE,null,a.revision()+5));
        science.setStatus(PublicationStatus.IN_REVIEW);actor("curator","CURATOR");
        assertThrows(ResponseStatusException.class,()->privacy.review("r1",true,"Checked",null));
        assertThrows(ResponseStatusException.class,()->privacy.review("r1",true,"",a.revision()));
    }
    @Test public void returnToDraftRevokesApprovalAndPublicationIsImmutable() {
        var a=privacy.save("r1",ScientificPrivacyAssessment.Classification.PERSONAL,"Protection",null);
        science.setAccessLevel("RESTRICTED");science.setStatus(PublicationStatus.IN_REVIEW);actor("curator","CURATOR");
        privacy.review("r1",true,"Checked",a.revision());privacy.clearReview("r1");
        assertThrows(ResponseStatusException.class,()->privacy.requirePublicationAllowed("r1","RESTRICTED"));
        science.setStatus(PublicationStatus.PUBLISHED);actor("author","USER");
        assertThrows(ResponseStatusException.class,()->privacy.save("r1",ScientificPrivacyAssessment.Classification.NONE,null,stored.get("r1").getRevision()));
    }
    @Test public void strictPolicyRequiresDeclarationAndRejectedReviewBlocksEvenNonSensitive() {
        privacy=new ScientificPrivacyService(assessments,records,resources,owners,events,true,org.mockito.Mockito.mock(ScientificResourceWriteLock.class));
        assertThrows(ResponseStatusException.class,()->privacy.requireSubmissionAllowed("r1","OPEN"));
        var a=privacy.save("r1",ScientificPrivacyAssessment.Classification.NONE,null,null);
        privacy.requireSubmissionAllowed("r1","OPEN");science.setStatus(PublicationStatus.IN_REVIEW);actor("curator","CURATOR");
        privacy.review("r1",false,"Check provenance",a.revision());
        assertThrows(ResponseStatusException.class,()->privacy.requirePublicationAllowed("r1","OPEN"));
    }
}
