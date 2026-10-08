package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Bounded organization lookup against ROR v2; never accepts a caller-supplied URL. */
@Service
public class RorLookupService {
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI base;
    private final boolean enabled;

    @Autowired
    public RorLookupService(ObjectMapper json, @Value("${repo.scientific.ror-lookup.enabled:true}") boolean enabled) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),
                URI.create("https://api.ror.org/v2/organizations"), enabled);
    }

    RorLookupService(ObjectMapper json, HttpClient http, URI base, boolean enabled) {
        this.json = json; this.http = http; this.base = base; this.enabled = enabled;
    }

    public List<Organization> search(String query) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta ROR desactivada.");
        if (query == null || query.isBlank() || query.trim().length() < 2 || query.length() > 100 ||
                !query.matches("[\\p{L}\\p{N} .&'()/-]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escriba entre 2 y 100 caracteres del nombre institucional.");
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        URI url = URI.create(base + "?query=" + encoded);
        try {
            HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "RedUniv-Repository/1.0")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ROR no respondió a la consulta.");
            JsonNode root = json.readTree(response.body());
            if (!root.path("items").isArray())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ROR devolvió datos incompletos.");
            List<Organization> results = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                if (results.size() >= 10) break;
                String id = item.path("id").asText("");
                if (!id.matches("https://ror\\.org/0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}")) continue;
                String label = "";
                for (JsonNode name : item.path("names")) {
                    if (name.path("types").isArray()) for (JsonNode type : name.path("types")) {
                        if ("ror_display".equals(type.asText())) label = name.path("value").asText("");
                    }
                }
                if (label.isBlank() && item.path("names").isArray() && !item.path("names").isEmpty())
                    label = item.path("names").get(0).path("value").asText("");
                if (!label.isBlank()) results.add(new Organization(label, id));
            }
            return results;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Consulta ROR interrumpida.");
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo contactar con ROR.");
        }
    }

    public record Organization(String name, String ror) {}
}
