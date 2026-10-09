package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.security.ResourceMutationGateFilter;
import edu.kit.datamanager.repo.service.ResourceMutationCoordinator;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.util.List;
import org.junit.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ResourceMutationGateFilterTest {
    @Before public void auth(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author","",List.of()));}
    @After public void cleanup(){SecurityContextHolder.clearContext();}
    private ResourceOwnershipRepository owners() {
        var owners=mock(ResourceOwnershipRepository.class);
        when(owners.findById("r1")).thenReturn(java.util.Optional.of(new ResourceOwnership("r1","author")));return owners;
    }
    @Test public void gatesEveryWriteVerbAcrossScientificAndLegacyPathsAndReleasesAfterFailure() throws Exception {
        for(String method:List.of("POST","PUT","PATCH","DELETE"))for(String path:List.of("/api/v1/scientific/r1/funding","/api/v1/dataresources/r1/data/file.csv","/%61pi/v1/scientific/%72%31/submit")) {
            var coordinator=mock(ResourceMutationCoordinator.class);var lease=mock(ResourceMutationCoordinator.Lease.class);
            var resources=mock(IDataResourceDao.class);when(resources.existsById("r1")).thenReturn(true);when(coordinator.acquire("r1")).thenReturn(lease);
            var request=new MockHttpServletRequest(method,path);var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
            doThrow(new IOException("simulated")).when(chain).doFilter(request,response);
            assertThrows(IOException.class,()->new ResourceMutationGateFilter(coordinator,resources,owners()).doFilter(request,response,chain));
            verify(coordinator).acquire("r1");verify(lease).close();
        }
    }
    @Test public void busyLeaseReturnsSafeRetryWithoutEnteringController() throws Exception {
        var coordinator=mock(ResourceMutationCoordinator.class);var resources=mock(IDataResourceDao.class);when(resources.existsById("r1")).thenReturn(true);
        when(coordinator.acquire("r1")).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Dataset ocupado."));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new ResourceMutationGateFilter(coordinator,resources,owners()).doFilter(new MockHttpServletRequest("POST","/api/v1/scientific/r1/publish"),response,chain);
        assertEquals(409,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals("1",response.getHeader("Retry-After"));verifyNoInteractions(chain);
    }
    @Test public void readsAndUnauthenticatedOrNonResourceOperationsDoNotTakeLease() throws Exception {
        var coordinator=mock(ResourceMutationCoordinator.class);var resources=mock(IDataResourceDao.class);var chain=mock(FilterChain.class);
        var filter=new ResourceMutationGateFilter(coordinator,resources,owners());
        filter.doFilter(new MockHttpServletRequest("GET","/api/v1/scientific/r1"),new MockHttpServletResponse(),chain);
        filter.doFilter(new MockHttpServletRequest("PUT","/api/v1/scientific/profiles/example"),new MockHttpServletResponse(),chain);
        SecurityContextHolder.clearContext();filter.doFilter(new MockHttpServletRequest("POST","/api/v1/scientific/r1/publish"),new MockHttpServletResponse(),chain);
        verifyNoInteractions(coordinator);
    }
    @Test public void unrelatedUserCannotTakeOrProbePrivateResourceLease() throws Exception {
        var coordinator=mock(ResourceMutationCoordinator.class);var resources=mock(IDataResourceDao.class);when(resources.existsById("r1")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other","",List.of()));
        var chain=mock(FilterChain.class);var request=new MockHttpServletRequest("PUT","/api/v1/scientific/r1");var response=new MockHttpServletResponse();
        new ResourceMutationGateFilter(coordinator,resources,owners()).doFilter(request,response,chain);
        verifyNoInteractions(coordinator);verify(chain).doFilter(request,response);
    }
}
