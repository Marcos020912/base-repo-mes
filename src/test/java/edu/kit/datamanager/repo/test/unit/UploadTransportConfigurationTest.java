package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.configuration.UploadTransportConfiguration;
import org.apache.catalina.connector.Connector;
import org.junit.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import static org.junit.Assert.*;

public class UploadTransportConfigurationTest {
    @Test public void continueIsAcknowledgedOnlyWhenServletReadsBody() {
        var factory=new TomcatServletWebServerFactory();
        new UploadTransportConfiguration().uploadContinueTiming().customize(factory);
        var connector=new Connector();
        factory.getTomcatConnectorCustomizers().forEach(customizer->customizer.customize(connector));
        assertEquals("onRead",connector.getProperty("continueResponseTiming"));
    }
}
