package edu.kit.datamanager.repo.test.integration;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import edu.kit.datamanager.repo.service.UsageMetricsService;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import static org.junit.Assert.*;
@RunWith(SpringRunner.class) @SpringBootTest @ActiveProfiles("test")
public class UsageMetricsIntegrationTest {
 @Autowired private UsageMetricsService usage;
 @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;
 @Autowired private UsageObservationRepository observations;
 @Autowired private ScientificRecordRepository records;
 private final String id="usage-fixture";
 @Before public void setup(){observations.deleteAll();var r=new ScientificRecord(id);r.setStatus(PublicationStatus.PUBLISHED);records.save(r);}
 @After public void cleanup(){observations.deleteAll();records.deleteById(id);}
 @Test public void aggregatesDoubleClicksAndDoesNotExposePseudonyms(){
  Instant at=Instant.now().minusSeconds(120);
  usage.record(id,UsageObservation.Kind.VIEW,"detail","127.0.0.1","human",at);
  usage.record(id,UsageObservation.Kind.VIEW,"detail","127.0.0.1","human",at.plusSeconds(29));
  usage.record(id,UsageObservation.Kind.VIEW,"detail","127.0.0.1","human",at.plusSeconds(60));
  usage.record(id,UsageObservation.Kind.DOWNLOAD,"/file:data.csv","127.0.0.1","human",at);
  var report=usage.report(null,null,id,true);assertEquals(2,report.views());assertEquals(1,report.downloads());assertEquals(2,report.rows().size());assertFalse(report.counterCertified());
  assertTrue(observations.findAll().stream().allMatch(o->o.getId().matches("[0-9a-f]{64}")));
  assertFalse(usage.report(null,null,id,false).rows().iterator().hasNext());
 }
 @Test public void publicJsonKeepsEmptyRows() throws Exception {
  var json=mapper.readTree(mapper.writeValueAsString(usage.report(null,null,id,false)));
  assertTrue(json.get("rows").isArray());assertEquals(0,json.get("rows").size());
 }
 @Test public void privateAndWithdrawnRecordsHaveNoPublicUsageReport(){
  var r=records.findById(id).orElseThrow();r.setStatus(PublicationStatus.DRAFT);records.save(r);
  usage.record(id,UsageObservation.Kind.VIEW,"detail","ip","agent",Instant.now());assertEquals(0,observations.count());
  var denied=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->usage.report(null,null,id,true));assertEquals(404,denied.getStatusCode().value());
 }
 @Test public void utcRetentionAndInvalidDatesAreBounded(){
  usage.record(id,UsageObservation.Kind.VIEW,"detail","ip","agent",Instant.now().minusSeconds(100*86400));
  usage.purge();assertEquals(0,observations.count());
  var today=LocalDate.now(ZoneOffset.UTC);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->usage.report(today.minusDays(90),today,null,false));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->usage.report(today,today.plusDays(1),null,false));
 }
}
