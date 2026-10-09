package edu.kit.datamanager.repo.service;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
public class DepositTaskServiceTest {
    private ResourceOwnershipRepository ownership;private IDataResourceDao resources;private ScientificRecordRepository records;private ScientificQualityService quality;private DepositTaskService service;
    @Before public void setup(){ownership=mock(ResourceOwnershipRepository.class);resources=mock(IDataResourceDao.class);records=mock(ScientificRecordRepository.class);quality=mock(ScientificQualityService.class);service=new DepositTaskService(ownership,resources,records,quality);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author","",List.of()));}
    @After public void clear(){SecurityContextHolder.clearContext();}
    @Test public void ownerScopedPagedTasksExcludePublishedAndOrphans(){
        var draft=new ScientificRecord("draft");var review=new ScientificRecord("review");review.setStatus(PublicationStatus.IN_REVIEW);var published=new ScientificRecord("published");published.setStatus(PublicationStatus.PUBLISHED);
        when(ownership.findByUsernameIgnoreCaseOrderByCreatedAtDesc("author")).thenReturn(List.of(new ResourceOwnership("draft","author"),new ResourceOwnership("published","author"),new ResourceOwnership("review","author"),new ResourceOwnership("deleted","author")));
        when(records.findAllById(any())).thenReturn(List.of(draft,review,published));
        when(resources.findAllById(any())).thenReturn(List.of(DataResource.factoryNewDataResource("draft"),DataResource.factoryNewDataResource("published"),DataResource.factoryNewDataResource("review")));
        var check=new ScientificQualityService.QualityCheck("license","Licencia",false,true);when(quality.inspect(review)).thenReturn(new ScientificQualityService.QualityReport(80,List.of("Licencia"),List.of(check)));
        var result=service.mine(1,1);assertEquals(2,result.total());assertEquals(2,result.pages());assertEquals("review",result.items().get(0).resourceId());assertEquals(PublicationStatus.IN_REVIEW,result.items().get(0).status());assertEquals("Esperar revisión de curación",result.items().get(0).nextAction());assertEquals(1,result.items().get(0).pending().size());verify(quality,never()).inspect(draft);verify(ownership).findByUsernameIgnoreCaseOrderByCreatedAtDesc("author");
    }
    @Test public void authenticationAndBounds(){SecurityContextHolder.clearContext();assertEquals(401,Assert.assertThrows(ResponseStatusException.class,()->service.mine(0,10)).getStatusCode().value());assertEquals(400,Assert.assertThrows(ResponseStatusException.class,()->service.mine(-1,10)).getStatusCode().value());assertEquals(400,Assert.assertThrows(ResponseStatusException.class,()->service.mine(0,51)).getStatusCode().value());}
    @Test public void emptyQueueHasNoInspections(){when(ownership.findByUsernameIgnoreCaseOrderByCreatedAtDesc("author")).thenReturn(List.of());when(records.findAllById(any())).thenReturn(List.of());when(resources.findAllById(any())).thenReturn(List.of());var result=service.mine(0,10);assertEquals(0,result.total());assertTrue(result.items().isEmpty());verifyNoInteractions(quality);}
    @Test public void emptyQueueSerializesItemsDespiteGlobalNonEmpty() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY);
        var json=mapper.readTree(mapper.writeValueAsString(new DepositTaskService.Tasks(List.of(),0,10,0,0)));
        assertTrue(json.has("items"));assertTrue(json.get("items").isArray());assertEquals(0,json.get("items").size());assertEquals(0,json.get("pages").asInt());
    }
}
