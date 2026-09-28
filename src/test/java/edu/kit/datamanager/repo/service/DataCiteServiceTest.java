package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;

public class DataCiteServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<JsonNode> sent = new AtomicReference<>();

    @Before
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/dois", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sent.set("GET".equals(method.get()) ? null : json.readTree(exchange.getRequestBody()));
            boolean draft = "POST".equals(method.get());
            byte[] response = ("{\"data\":{\"id\":\"10.1234/abcd\",\"attributes\":{\"doi\":\"10.1234/abcd\",\"state\":\""
                    + (draft ? "draft" : "findable") + "\"}}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/vnd.api+json");
            exchange.sendResponseHeaders(draft ? 201 : 200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
    }

    @After
    public void stop() { server.stop(0); }

    private DataCiteService client(boolean enabled) {
        return new DataCiteService(json, HttpClient.newHttpClient(), enabled,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "repo-id", "secret", "10.1234", true);
    }

    @Test
    public void reserveDraftOmitsPublishEventAndUsesAutoSuffix() {
        var result = client(true).reserveDraft();
        assertEquals("10.1234/abcd", result.doi());
        assertEquals("draft", result.state());
        assertEquals("POST", method.get());
        assertEquals("/dois", path.get());
        assertEquals("10.1234", sent.get().path("data").path("attributes").path("prefix").asText());
        assertFalse(sent.get().path("data").path("attributes").has("event"));
        assertFalse(sent.get().path("data").path("attributes").has("doi"));
        assertTrue(authorization.get().startsWith("Basic "));
    }

    @Test
    public void publishSendsCompleteMetadataAndEvent() {
        var result = client(true).publish("10.1234/abcd", Map.of("url", "https://datos.reduniv.edu.cu/datasets/1"));
        assertEquals("findable", result.state());
        assertEquals("PUT", method.get());
        assertEquals("/dois/10.1234/abcd", path.get());
        assertEquals("publish", sent.get().path("data").path("attributes").path("event").asText());
        assertEquals("https://datos.reduniv.edu.cu/datasets/1", sent.get().path("data").path("attributes").path("url").asText());
    }

    @Test
    public void disabledClientDoesNotSendAnything() {
        assertThrows(ResponseStatusException.class, () -> client(false).reserveDraft());
        assertNull(method.get());
    }

    @Test
    public void authenticatedGetReadsCurrentState() {
        var result = client(true).get("10.1234/abcd");
        assertEquals("GET", method.get());
        assertEquals("/dois/10.1234/abcd", path.get());
        assertEquals("findable", result.state());
    }
}
