package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.domain.UsageObservation;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;
public class UsageObservationTest {
 @Test public void slidingDoubleClickRetainsLatestWithinThirtySeconds(){
  Instant at=Instant.parse("2026-10-09T01:00:00Z");var event=new UsageObservation("key","r",UsageObservation.Kind.VIEW,at);
  event.observe(at.plusSeconds(29));event.observe(at.plusSeconds(59));assertEquals(1,event.getRequests());assertEquals(at.plusSeconds(59),event.getLastSeenAt());
  event.observe(at.plusSeconds(90));assertEquals(2,event.getRequests());event.observe(at);assertEquals(2,event.getRequests());
 }
}
