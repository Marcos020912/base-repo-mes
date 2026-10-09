package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.domain.DoiRegistration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;
public class DoiRegistrationEvidenceTest {
 @Test public void failureDoesNotInventSuccessfulSynchronizationOrMetadataRevision(){
  var registration=new DoiRegistration("v:r","r","VERSION","10.1234/r","api.test.datacite.org");
  var at=Instant.parse("2026-10-01T00:00:00Z");
  registration.observe("RESERVE","ERROR","failure",null,false,at);
  assertEquals(at,registration.getLastAttemptAt());assertNull(registration.getLastSyncedAt());assertNull(registration.getRegisteredAt());assertEquals(Long.valueOf(0),registration.getMetadataVersion());
  registration.observe("RESERVE","DRAFT",null,null,false,at.plusSeconds(1));
  registration.observe("PUBLISH","FINDABLE",null,"https://example.invalid/datasets/r",true,at.plusSeconds(2));
  assertEquals(at.plusSeconds(1),registration.getRegisteredAt());assertEquals(at.plusSeconds(2),registration.getPublishedAt());assertEquals(Long.valueOf(1),registration.getMetadataVersion());
  registration.observe("UPDATE_URL","FINDABLE",null,"https://example.invalid/datasets/new",false,at.plusSeconds(3));
  assertEquals(Long.valueOf(1),registration.getMetadataVersion());assertEquals("https://example.invalid/datasets/new",registration.getLandingPageUrl());
  registration.observe("PUBLISH","ERROR","retry",null,true,at.plusSeconds(4));
  assertEquals(at.plusSeconds(3),registration.getLastSyncedAt());assertEquals(at.plusSeconds(4),registration.getLastAttemptAt());assertEquals(Long.valueOf(1),registration.getMetadataVersion());
 }
 @Test public void legacyUnknownDatesRemainUnknownAndReconciliationIsNotMetadataProof(){
  var registration=new DoiRegistration();var at=Instant.parse("2026-10-01T00:00:00Z");
  registration.observe("RECONCILE","FINDABLE",null,null,false,at);
  assertNull(registration.getMetadataVersion());assertNull(registration.getLandingPageUrl());
  registration.observe("PUBLISH","FINDABLE",null,"https://example.invalid/datasets/r",true,at.plusSeconds(1));
  assertEquals(Long.valueOf(1),registration.getMetadataVersion());assertEquals(at,registration.getPublishedAt());
 }
}
