package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import edu.kit.datamanager.repo.security.ResourceOwnershipAuthorizationFilter;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.FilterChain;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class ScientificContentReadGuardTest {
    @Parameterized.Parameters(name="{0}/{1}/{2}/{3}/{4}")
    public static Collection<Object[]> cases() {
        List<Object[]> values=new ArrayList<>();
        for(PublicationStatus state:PublicationStatus.values())
            for(String access:List.of("OPEN","RESTRICTED","FUTURE","EXPIRED"))
                for(String method:List.of("GET","HEAD"))
                    for(String path:List.of("/data/file.csv","/archive"))
                        for(String actor:List.of("anonymous","other","author","curator","admin"))
                            values.add(new Object[]{state,access,method,path,actor});
        return values;
    }
    private final PublicationStatus state;private final String access,method,path,actor;
    public ScientificContentReadGuardTest(PublicationStatus state,String access,String method,String path,String actor){
        this.state=state;this.access=access;this.method=method;this.path=path;this.actor=actor;
    }
    @After public void cleanup(){SecurityContextHolder.clearContext();}
    @Test public void publicAclCannotBypassScientificPublicationOrAccessState() throws Exception {
        SecurityContextHolder.clearContext();
        if(!actor.equals("anonymous")) {
            String role=actor.equals("admin")?"ADMINISTRATOR":actor.equals("curator")?"CURATOR":"USER";
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
        }
        var ownership=mock(ResourceOwnershipRepository.class);var records=mock(ScientificRecordRepository.class);
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1","author")));
        ScientificRecord record=new ScientificRecord("r1");record.setStatus(state);
        record.setAccessLevel(access.equals("FUTURE")||access.equals("EXPIRED")?"EMBARGOED":access);
        if(access.equals("FUTURE"))record.setEmbargoUntil(Instant.now().plusSeconds(3600));
        if(access.equals("EXPIRED"))record.setEmbargoUntil(Instant.now().minusSeconds(3600));
        when(records.findById("r1")).thenReturn(Optional.of(record));
        var request=new MockHttpServletRequest(method,"/api/v1/dataresources/r1"+path);
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new ResourceOwnershipAuthorizationFilter(ownership,records).doFilter(request,response,chain);
        boolean privileged=List.of("author","curator","admin").contains(actor);
        boolean publicRead=state==PublicationStatus.PUBLISHED&&(access.equals("OPEN")||access.equals("EXPIRED"));
        if(privileged||publicRead)verify(chain).doFilter(request,response);
        else {verifyNoInteractions(chain);assertEquals(state==PublicationStatus.PUBLISHED?403:404,response.getStatus());}
    }
}
