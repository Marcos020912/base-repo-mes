package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import edu.kit.datamanager.repo.security.ResourceOwnershipAuthorizationFilter;
import jakarta.servlet.FilterChain;
import java.util.*;
import org.junit.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EncodedScientificResourceGuardTest {
    @After public void cleanup(){SecurityContextHolder.clearContext();}
    @Test public void encodedPrefixOrIdentifierCannotBypassPublishedImmutabilityEvenForAdmin() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"))));
        for(String path:List.of("/api/v1/dataresources/%72%31","/%61pi/v1/dataresources/r1","/api/v1/dataresources/r1")) {
            var records=mock(ScientificRecordRepository.class);var ownership=mock(ResourceOwnershipRepository.class);var record=new ScientificRecord("r1");record.setStatus(PublicationStatus.PUBLISHED);
            when(records.findById("r1")).thenReturn(Optional.of(record));var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
            new ResourceOwnershipAuthorizationFilter(ownership,records).doFilter(new MockHttpServletRequest("DELETE",path),response,chain);
            assertEquals(409,response.getStatus());verifyNoInteractions(chain);
        }
    }
    @Test public void canonicalContainerPathTakesPrecedenceOverRawUri() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other","",List.of()));
        var records=mock(ScientificRecordRepository.class);var record=new ScientificRecord("r1");when(records.findById("r1")).thenReturn(Optional.of(record));
        var request=new MockHttpServletRequest("GET","/encoded/uri");request.setServletPath("/api/v1/dataresources/r1");var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new ResourceOwnershipAuthorizationFilter(mock(ResourceOwnershipRepository.class),records).doFilter(request,response,chain);
        assertEquals(404,response.getStatus());verifyNoInteractions(chain);
    }
    @Test public void localUsersCannotUseLegacyCollectionsToBypassScientificVisibility() throws Exception {
        for(String actor:List.of("USER","CURATOR","ADMINISTRATOR"))for(String path:List.of("/api/v1/dataresources/","/api/v1/dataresources/search","/api/v1/dataresources/search/data","/api/v1/dataresources/%73earch")) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("local","",List.of(new SimpleGrantedAuthority("ROLE_"+actor))));
            var request=new MockHttpServletRequest(path.endsWith("/")?"GET":"POST",path);var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
            var records=mock(ScientificRecordRepository.class);var ownership=mock(ResourceOwnershipRepository.class);
            new ResourceOwnershipAuthorizationFilter(ownership,records).doFilter(request,response,chain);
            if(actor.equals("USER")){assertEquals(403,response.getStatus());verifyNoInteractions(chain);}else verify(chain).doFilter(request,response);
            verifyNoInteractions(records,ownership);
        }
    }

}
