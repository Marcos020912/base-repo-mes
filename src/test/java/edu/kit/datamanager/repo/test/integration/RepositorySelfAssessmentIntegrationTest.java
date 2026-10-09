package edu.kit.datamanager.repo.test.integration;
import edu.kit.datamanager.repo.domain.RepositorySelfAssessment.State;
import edu.kit.datamanager.repo.repository.RepositorySelfAssessmentRepository;
import edu.kit.datamanager.repo.service.RepositorySelfAssessmentService;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
@RunWith(SpringRunner.class) @SpringBootTest @ActiveProfiles("test")
public class RepositorySelfAssessmentIntegrationTest {
 @Autowired private RepositorySelfAssessmentService service;
 @Autowired private RepositorySelfAssessmentRepository repository;
 @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;
 @Autowired private edu.kit.datamanager.repo.web.impl.RepositorySelfAssessmentController controller;
 @Before @After public void clean(){repository.deleteAll();}
 @Test public void sixteenRequirementsNeverClaimCertification(){
  var report=service.report();assertEquals(16,report.items().size());assertFalse(report.certified());
  assertEquals("R01",report.items().get(0).id());assertEquals("R16",report.items().get(15).id());
  assertTrue(report.items().stream().allMatch(i->i.state()==State.NOT_STARTED&&i.revision()==null));
 }
 @Test public void jsonKeepsEmptyEvidenceAndNullInitialRevision() throws Exception {
  var json=mapper.readTree(mapper.writeValueAsString(service.report()));
  assertTrue(json.get("items").get(0).get("evidence").isArray());assertEquals(0,json.get("items").get(0).get("evidence").size());
  assertTrue(json.get("items").get(0).get("revision").isNull());
 }
 @Test public void savesEvidenceWithOptimisticRevisionAndReviewPrerequisites(){
  var first=service.save("R01",null,State.IN_PROGRESS,"Editorial","Draft",List.of(),"admin");
  assertNotNull(first.revision());assertEquals("admin",first.updatedBy());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save("R01",null,State.IN_PROGRESS,null,null,List.of(),"admin")).getStatusCode().value());
  assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save("R01",first.revision(),State.READY_FOR_REVIEW,"Editorial","Draft",List.of(),"admin")).getStatusCode().value());
  var ready=service.save("R01",first.revision(),State.READY_FOR_REVIEW,"Editorial","Statement",List.of("https://example.org/policy","https://example.org/policy"),"admin");
  assertTrue(ready.revision()>first.revision());assertEquals(1,ready.evidence().size());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save("R01",first.revision(),State.IN_PROGRESS,null,null,List.of(),"admin")).getStatusCode().value());
  assertFalse(service.report().certified());
 }
 @Test public void editorialPermissionsAreEnforcedByBackend(){
  var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
  var input=new edu.kit.datamanager.repo.web.impl.RepositorySelfAssessmentController.Input(null,State.IN_PROGRESS,null,null,List.of());
  try {
   context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("reader","unused","ROLE_USER"));
   assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.report());
   assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.update("R01",input));
   context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("curator","unused","ROLE_CURATOR"));
   assertEquals(16,controller.report().getBody().items().size());
   assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.update("R01",input));
   context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("admin","unused","ROLE_ADMINISTRATOR"));
   assertEquals("admin",controller.update("R01",input).updatedBy());
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
 @Test public void rejectsUnsafeLinksUnknownRequirementsAndOversizedFields(){
  for(String url:List.of("javascript:alert(1)","http://example.org","https://user:pass@example.org/path","https://example.org/?token=private"))
   assertEquals(400,assertThrows(ResponseStatusException.class,()->service.save("R01",null,State.IN_PROGRESS,null,null,List.of(url),"admin")).getStatusCode().value());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.save("R17",null,State.IN_PROGRESS,null,null,List.of(),"admin")).getStatusCode().value());
  assertThrows(ResponseStatusException.class,()->service.save("R01",null,State.IN_PROGRESS,"x".repeat(256),null,List.of(),"admin"));
  assertEquals(0,repository.count());
 }
}
