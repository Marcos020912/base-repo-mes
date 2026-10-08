package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;

public class RorLookupServiceTest {
    @Test public void mapsOnlyNamedRorOrganizationsAndEncodesQuery() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/v2/organizations", exchange -> {
            seen.set(exchange.getRequestURI().getRawQuery());
            byte[] body = ("{\"items\":[{\"id\":\"https://ror.org/03yrm5c26\",\"names\":[{\"value\":\"Universidad de La Habana\",\"types\":[\"ror_display\"]}]}," +
                    "{\"id\":\"https://invalid.example/abc\",\"names\":[{\"value\":\"Falso\"}]}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var lookup = new RorLookupService(new ObjectMapper(), HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/organizations"), true);
            var result = lookup.search("Universidad de La Habana");
            assertEquals(1, result.size());
            assertEquals("Universidad de La Habana", result.get(0).name());
            assertEquals("https://ror.org/03yrm5c26", result.get(0).ror());
            assertTrue(seen.get().contains("Universidad+de+La+Habana"));
            assertThrows(ResponseStatusException.class, () -> lookup.search("*"));
        } finally { server.stop(0); }
    }
}
