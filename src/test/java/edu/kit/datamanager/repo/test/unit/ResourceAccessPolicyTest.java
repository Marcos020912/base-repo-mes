package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.security.ResourceOwnershipAuthorizationFilter;
import java.time.Instant;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ResourceAccessPolicyTest {
    private ScientificRecordRepository records;
    private ResourceOwnershipRepository owners;
    private ResourceOwnershipAuthorizationFilter filter;

    @Before public void setUp() {
        records = mock(ScientificRecordRepository.class);
        owners = mock(ResourceOwnershipRepository.class);
        filter = new ResourceOwnershipAuthorizationFilter(owners, records);
        when(owners.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
    }

    @After public void tearDown() { SecurityContextHolder.clearContext(); }

    private int request(ScientificRecord record, String username, String path) throws Exception {
        when(records.findById("r1")).thenReturn(Optional.of(record));
        if (username != null) SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dataresources/r1/" + path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test public void restrictedFileCannotBeReadThroughLegacyEndpoint() throws Exception {
        ScientificRecord record = new ScientificRecord("r1");
        record.setStatus(PublicationStatus.PUBLISHED);
        record.setAccessLevel("RESTRICTED");
        assertEquals(403, request(record, null, "data/private.csv"));
        assertEquals(403, request(record, "other", "archive"));
        assertEquals(200, request(record, "author", "data/private.csv"));
    }

    @Test public void embargoExpiresForPublicFileReads() throws Exception {
        ScientificRecord record = new ScientificRecord("r1");
        record.setStatus(PublicationStatus.PUBLISHED);
        record.setAccessLevel("EMBARGOED");
        record.setEmbargoUntil(Instant.now().plusSeconds(3600));
        assertEquals(403, request(record, null, "data/paper.pdf"));
        record.setEmbargoUntil(Instant.now().minusSeconds(3600));
        assertEquals(200, request(record, null, "data/paper.pdf"));
    }
}
