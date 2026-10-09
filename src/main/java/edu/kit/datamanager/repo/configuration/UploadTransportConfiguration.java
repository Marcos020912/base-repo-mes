package edu.kit.datamanager.repo.configuration;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Let authorization inspect headers before clients send large upload bodies. */
@Configuration
public class UploadTransportConfiguration {
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> uploadContinueTiming() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (!connector.setProperty("continueResponseTiming", "onRead"))
                throw new IllegalStateException("Tomcat must support continueResponseTiming=onRead.");
        });
    }
}
