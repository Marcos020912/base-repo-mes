package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Backend-only DataCite REST adapter. No request is sent unless explicitly enabled. */
@Service
public class DataCiteService {
    private static final Pattern DOI = Pattern.compile("^10\\.\\d{4,9}/[A-Za-z0-9._;()/:-]+$");
    private final ObjectMapper json;
    private final HttpClient http;
    private final boolean enabled;
    private final URI baseUri;
    private final String repositoryId;
    private final String password;
    private final String prefix;
    private final boolean allowLocalTestServer;

    @Autowired
    public DataCiteService(ObjectMapper json,
            @Value("${repo.datacite.enabled:false}") boolean enabled,
            @Value("${repo.datacite.api-url:https://api.test.datacite.org}") String apiUrl,
            @Value("${repo.datacite.repository-id:}") String repositoryId,
            @Value("${repo.datacite.password:}") String password,
            @Value("${repo.datacite.prefix:}") String prefix) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                enabled, apiUrl, repositoryId, password, prefix, false);
    }

    /** Allows an in-process HTTP server to exercise requests without DataCite credentials. */
    DataCiteService(ObjectMapper json, HttpClient http, boolean enabled, String apiUrl,
                    String repositoryId, String password, String prefix, boolean allowLocalTestServer) {
        this.json = json;
        this.http = http;
        this.enabled = enabled;
        this.baseUri = URI.create(apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl);
        this.repositoryId = repositoryId;
        this.password = password;
        this.prefix = prefix;
        this.allowLocalTestServer = allowLocalTestServer;
    }

    public boolean isEnabled() { return enabled; }

    /** Draft is only a reservation, not a resolvable DOI. DataCite generates the suffix. */
    public DoiResponse reserveDraft() {
        requireConfigured();
        DoiResponse response = send("POST", "/dois", Map.of("prefix", prefix), 201);
        if (!response.doi().startsWith(prefix + "/") || !"draft".equalsIgnoreCase(response.state()))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DataCite no confirmó una reserva Draft del prefijo configurado.");
        return response;
    }

    /** Transitions a Draft/Registered record to Findable using complete, validated metadata. */
    public DoiResponse publish(String doi, Map<String, Object> metadata) {
        requireConfigured();
        requireDoi(doi);
        Map<String, Object> attributes = new LinkedHashMap<>(metadata);
        attributes.put("event", "publish");
        attributes.put("doi", doi);
        DoiResponse response = send("PUT", "/dois/" + doi, attributes, 200);
        if (!doi.equalsIgnoreCase(response.doi()) || !"findable".equalsIgnoreCase(response.state()))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DataCite no confirmó DOI Findable.");
        return response;
    }

    public DoiResponse updateMetadata(String doi, Map<String, Object> metadata) {
        requireConfigured();
        requireDoi(doi);
        if (metadata.containsKey("event") || metadata.containsKey("doi"))
            throw new IllegalArgumentException("Use the dedicated state transition to change a DOI state.");
        DoiResponse response = send("PUT", "/dois/" + doi, metadata, 200);
        if (!doi.equalsIgnoreCase(response.doi()))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DataCite devolvió un DOI diferente.");
        return response;
    }

    public DoiResponse updateUrl(String doi, URI landingPage) {
        if (!"https".equalsIgnoreCase(landingPage.getScheme()) || landingPage.getHost() == null)
            throw new IllegalArgumentException("The DOI landing page must be an HTTPS URL.");
        return updateMetadata(doi, Map.of("url", landingPage.toString()));
    }

    /** Authenticated lookup also sees Draft and Registered records. */
    public DoiResponse get(String doi) {
        requireConfigured();
        requireDoi(doi);
        DoiResponse response = send("GET", "/dois/" + doi, null, 200);
        if (!doi.equalsIgnoreCase(response.doi()))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DataCite devolvió un DOI diferente.");
        return response;
    }

    private DoiResponse send(String method, String path, Map<String, Object> attributes, int expectedStatus) {
        try {
            String basic = Base64.getEncoder().encodeToString((repositoryId + ":" + password).getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.api+json")
                    .header("Authorization", "Basic " + basic)
                    .header("User-Agent", "RedUniv-Repository/1.0 (https://datos.reduniv.edu.cu)");
            if (attributes == null) builder.GET();
            else {
                byte[] body = json.writeValueAsBytes(Map.of("data", Map.of("type", "dois", "attributes", attributes)));
                builder.header("Content-Type", "application/vnd.api+json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expectedStatus)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DataCite rechazó la operación; HTTP " + response.statusCode() + ".");
            JsonNode data = json.readTree(response.body()).path("data");
            String doi = data.path("attributes").path("doi").asText(data.path("id").asText());
            String state = data.path("attributes").path("state").asText();
            if (!DOI.matcher(doi).matches() || state.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DataCite devolvió una respuesta incompleta.");
            return new DoiResponse(doi, state);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "La conexión con DataCite fue interrumpida.");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo contactar o interpretar la respuesta de DataCite.");
        }
    }

    private void requireConfigured() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integración DOI desactivada.");
        if (repositoryId.isBlank() || password.isBlank() || !prefix.matches("10\\.\\d{4,9}"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falta configuración de DataCite.");
        if (!(allowLocalTestServer && "http".equals(baseUri.getScheme()) && "127.0.0.1".equals(baseUri.getHost()))
                && (!"https".equalsIgnoreCase(baseUri.getScheme()) || !("api.test.datacite.org".equals(baseUri.getHost()) || "api.datacite.org".equals(baseUri.getHost()))))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "URL de DataCite no permitida.");
    }

    private static void requireDoi(String doi) {
        if (doi == null || !DOI.matcher(doi).matches()) throw new IllegalArgumentException("Invalid DOI");
    }

    public record DoiResponse(String doi, String state) {}
}
