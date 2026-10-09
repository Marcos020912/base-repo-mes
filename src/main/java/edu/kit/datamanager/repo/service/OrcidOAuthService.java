package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.OrcidOAuthState;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Authenticates control of an ORCID account for one selected creator; does not verify the displayed name. */
@Service
public class OrcidOAuthService {
    private final OrcidOAuthStateService states;
    private final ScientificRecordRepository records;
    private final ResourceOwnershipRepository ownership;
    private final LocalUserRepository users;
    private final IDataResourceDao resources;
    private final ScientificCreatorRepository creators;
    private final ScientificRecordEventRepository events;
    private final ObjectMapper json;
    private final HttpClient http;
    private final ScientificResourceWriteLock writeLock;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final URI orcidBase;

    @Autowired
    public OrcidOAuthService(OrcidOAuthStateService states, ScientificRecordRepository records,
            ResourceOwnershipRepository ownership, LocalUserRepository users, IDataResourceDao resources,
            ScientificCreatorRepository creators, ScientificRecordEventRepository events, ObjectMapper json,
            @Value("${repo.scientific.orcid.enabled:false}") boolean enabled,
            @Value("${repo.scientific.orcid.environment:sandbox}") String environment,
            @Value("${repo.scientific.orcid.client-id:}") String clientId,
            @Value("${repo.scientific.orcid.client-secret:}") String clientSecret,
            @Value("${repo.scientific.orcid.redirect-uri:}") String redirectUri,
            ScientificResourceWriteLock writeLock, org.springframework.transaction.PlatformTransactionManager manager) {
        this(states, records, ownership, users, resources, creators, events, json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), enabled,
                clientId, clientSecret, redirectUri,
                "production".equals(environment) ? URI.create("https://orcid.org") : URI.create("https://sandbox.orcid.org"), writeLock, manager);
        if (!"production".equals(environment) && !"sandbox".equals(environment))
            throw new IllegalArgumentException("Entorno ORCID no válido.");
    }

    OrcidOAuthService(OrcidOAuthStateService states, ScientificRecordRepository records,
            ResourceOwnershipRepository ownership, LocalUserRepository users, IDataResourceDao resources,
            ScientificCreatorRepository creators, ScientificRecordEventRepository events, ObjectMapper json,
            HttpClient http, boolean enabled, String clientId, String clientSecret, String redirectUri, URI orcidBase,
            ScientificResourceWriteLock writeLock, org.springframework.transaction.PlatformTransactionManager manager) {
        this.states = states; this.records = records; this.ownership = ownership; this.users = users;
        this.resources = resources; this.creators = creators; this.events = events; this.json = json;
        this.http = http; this.enabled = enabled; this.clientId = clientId; this.clientSecret = clientSecret;
        this.redirectUri = redirectUri == null || redirectUri.isBlank() ? null : URI.create(redirectUri);
        this.orcidBase = orcidBase;
        this.writeLock=writeLock; this.transactions=new org.springframework.transaction.support.TransactionTemplate(manager);
    }

    public boolean isEnabled() { return configured(); }

    public String start(String resourceId, Long creatorId, String username) {
        requireConfigured(); requireEditable(resourceId, creatorId, username);
        String state = states.create(resourceId, creatorId, username);
        return orcidBase + "/oauth/authorize?client_id=" + enc(clientId) + "&response_type=code&scope="
                + enc("/authenticate") + "&redirect_uri=" + enc(redirectUri.toString()) + "&state=" + state;
    }

    public Result finish(String state, String code, String error) {
        requireConfigured();
        OrcidOAuthState pending = states.consume(state);
        if (error != null || code == null || code.isBlank() || code.length() > 2048)
            return new Result(pending.getResourceId(), false);
        requireEditable(pending.getResourceId(), pending.getCreatorId(), pending.getUsername());
        String orcid;
        try { orcid = exchange(code); }
        catch (ResponseStatusException failure) { return new Result(pending.getResourceId(), false); }
        attach(pending, orcid);
        return new Result(pending.getResourceId(), true);
    }

    private void requireConfigured() {
        if (!configured())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ORCID no está configurado.");
    }

    private boolean configured() {
        return enabled && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && redirectUri.getHost() != null && redirectUri.getFragment() == null
                && "/api/v1/scientific/orcid/callback".equals(redirectUri.getPath())
                && ("https".equals(redirectUri.getScheme()) ||
                    !"https://orcid.org".equals(orcidBase.toString())
                    && "http".equals(redirectUri.getScheme()) && "localhost".equals(redirectUri.getHost()));
    }

    private void requireEditable(String resourceId, Long creatorId, String username) {
        var user = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!user.isEnabled() || !user.isVerified() || ownership.findById(resourceId)
                .filter(item -> item.getUsername().equalsIgnoreCase(username)).isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede autenticar ORCID.");
        if (records.findById(resourceId).filter(item -> item.getStatus() == PublicationStatus.DRAFT).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede editar un borrador.");
        var resource = resources.findById(resourceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (resource.getCreators().stream().noneMatch(item -> creatorId.equals(item.getId())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Autor no encontrado.");
    }

    private String exchange(String code) {
        String form = "client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret)
                + "&grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(redirectUri.toString());
        HttpRequest request = HttpRequest.newBuilder(orcidBase.resolve("/oauth/token"))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var body = response.body()) { bytes = body.readNBytes(65537); }
            if (response.statusCode() != 200 || bytes.length > 65536)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ORCID rechazó la autenticación.");
            JsonNode token = json.readTree(bytes);
            String orcid = token.path("orcid").asText("");
            if (!orcid.matches("\\d{4}-\\d{4}-\\d{4}-[\\dX]{4}"))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ORCID no devolvió un identificador válido.");
            return orcid;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Consulta ORCID interrumpida.");
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con ORCID.");
        }
    }

    public void attach(OrcidOAuthState pending, String orcid) {
        transactions.executeWithoutResult(status -> {
        writeLock.acquireEditable(pending.getResourceId());
        requireEditable(pending.getResourceId(), pending.getCreatorId(), pending.getUsername());
        ScientificCreator creator = creators.findByResourceIdAndCreatorId(pending.getResourceId(), pending.getCreatorId())
                .orElseGet(() -> new ScientificCreator(pending.getResourceId(), pending.getCreatorId(), null, null, null));
        creator.authenticateOrcid(orcid, pending.getUsername(), Instant.now());
        creators.save(creator);
        events.save(new ScientificRecordEvent(pending.getResourceId(), pending.getUsername(),
                "ORCID_AUTHENTICATED", "creatorId=" + pending.getCreatorId()));
        });
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public record Result(String resourceId, boolean authenticated) {}
}
