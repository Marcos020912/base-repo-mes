package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.web.impl.MailDeliveryInfoController;
import org.junit.Test;
import static org.junit.Assert.*;
public class MailDeliveryInfoTest {
    @Test public void standardMailDoesNotExposePreview() {
        var info = new MailDeliveryInfoController("SMTP", "http://localhost:8025/").delivery();
        assertEquals("SMTP", info.mode()); assertNull(info.previewUrl());
    }
    @Test public void localCaptureExposesOnlyLoopbackViewer() {
        var info = new MailDeliveryInfoController("LOCAL_CAPTURE", "http://localhost:8025/").delivery();
        assertEquals("LOCAL_CAPTURE", info.mode()); assertEquals("http://localhost:8025/",info.previewUrl());
    }
    @Test public void rejectsRemoteCredentialsAndCodeParameters() {
        for (String url : new String[]{"https://evil.invalid/", "http://user:pass@localhost:8025/", "http://localhost:8025/?code=123456", "http://localhost:8025/private"}) {
            try {new MailDeliveryInfoController("LOCAL_CAPTURE",url);fail("Unsafe preview accepted");}
            catch (IllegalArgumentException expected) { }
        }
    }
}
