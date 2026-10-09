package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.LocalRole;
import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.domain.OrcidOAuthState;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrcidOAuthServiceTest {
    private final OrcidOAuthStateService states = mock(OrcidOAuthStateService.class);
    private final ScientificRecordRepository records = mock(ScientificRecordRepository.class);
    private final ResourceOwnershipRepository ownership = mock(ResourceOwnershipRepository.class);
    private final LocalUserRepository users = mock(LocalUserRepository.class);
    private final IDataResourceDao resources = mock(IDataResourceDao.class);
    private final ScientificCreatorRepository creators = mock(ScientificCreatorRepository.class);
    private final ScientificRecordEventRepository events = mock(ScientificRecordEventRepository.class);
    private HttpServer server;
    private OrcidOAuthService service;
    private AtomicReference<String> requestBody;

    private org.springframework.transaction.PlatformTransactionManager transactionManager() {
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));return manager;
    }
    @Before public void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        requestBody = new AtomicReference<>();
        server.createContext("/oauth/token", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"orcid\":\"0000-0002-1825-0097\",\"access_token\":\"private\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        service = new OrcidOAuthService(states, records, ownership, users, resources, creators, events,
                new ObjectMapper(), HttpClient.newHttpClient(), true, "APP-TEST", "secret", 
                "https://datos.example/api/v1/scientific/orcid/callback",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                mock(ScientificResourceWriteLock.class), transactionManager());
        LocalUser ana = new LocalUser("ana", "hash", LocalRole.USER); ana.setVerified(true);
        when(users.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));
        ResourceOwnership owner = mock(ResourceOwnership.class); when(owner.getUsername()).thenReturn("ana");
        when(ownership.findById("r1")).thenReturn(Optional.of(owner));
        when(records.findById("r1")).thenReturn(Optional.of(new ScientificRecord("r1")));
        Agent agent = mock(Agent.class); when(agent.getId()).thenReturn(7L);
        DataResource resource = mock(DataResource.class); when(resource.getCreators()).thenReturn(Set.of(agent));
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        when(states.create("r1", 7L, "ana")).thenReturn("a".repeat(64));
        when(states.consume("a".repeat(64))).thenReturn(new OrcidOAuthState("hash", "r1", 7L, "ana", Instant.now().plusSeconds(60)));
    }

    @After public void cleanup() { if (server != null) server.stop(0); }

    @Test public void authorizationCodeIsExchangedServerSideAndOnlyAuthenticatedIdIsSaved() {
        String url = service.start("r1", 7L, "ana");
        assertTrue(url.contains("scope=%2Fauthenticate"));
        assertTrue(url.contains("state=" + "a".repeat(64)));
        assertFalse(url.contains("secret"));
        var result = service.finish("a".repeat(64), "one-time-code", null);
        assertTrue(result.authenticated());
        assertTrue(requestBody.get().contains("code=one-time-code"));
        assertTrue(requestBody.get().contains("client_secret=secret"));
        verify(creators).save(argThat(item -> "0000-0002-1825-0097".equals(item.getOrcid())
                && item.getOrcidAuthenticatedAt() != null && "ana".equals(item.getOrcidAuthenticatedBy())));
    }

    @Test public void cancelledAuthorizationDoesNotCallTokenEndpoint() {
        assertFalse(service.finish("a".repeat(64), null, "access_denied").authenticated());
        assertNull(requestBody.get());
        verify(creators, never()).save(any());
    }

    @Test public void anotherUserCannotStartAndPublishedRecordCannotBeLinked() {
        assertThrows(ResponseStatusException.class, () -> service.start("r1", 7L, "bob"));
        var published = new ScientificRecord("r1");
        published.setStatus(edu.kit.datamanager.repo.domain.PublicationStatus.PUBLISHED);
        when(records.findById("r1")).thenReturn(Optional.of(published));
        assertThrows(ResponseStatusException.class, () -> service.start("r1", 7L, "ana"));
        verify(states, never()).create(any(), any(), any());
    }

    @Test public void disabledAccountAtCallbackCannotLinkAnIdentity() {
        LocalUser disabled = new LocalUser("ana", "hash", LocalRole.USER);
        disabled.setVerified(true); disabled.setEnabled(false);
        when(users.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(disabled));
        assertThrows(ResponseStatusException.class, () -> service.finish("a".repeat(64), "code", null));
        assertNull(requestBody.get());
        verify(creators, never()).save(any());
    }
}
