package edu.kit.datamanager.repo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.service.ResourceMutationCoordinator;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/** Covers both scientific workflow and legacy resource/file mutation routes. */
@Component
public class ResourceMutationGateFilter extends OncePerRequestFilter {
    private final ResourceMutationCoordinator coordinator;
    private final IDataResourceDao resources;
    private final ObjectMapper json=new ObjectMapper();
    private final ResourceOwnershipRepository ownership;
    public ResourceMutationGateFilter(ResourceMutationCoordinator coordinator,IDataResourceDao resources,ResourceOwnershipRepository ownership) {
        this.coordinator=coordinator;this.resources=resources;this.ownership=ownership;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !java.util.Set.of("POST","PUT","PATCH","DELETE").contains(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated()) {chain.doFilter(request,response);return;}
        String path=ResourceRequestPath.path(request),prefix;
        if(path.startsWith("/api/v1/scientific/"))prefix="/api/v1/scientific/";
        else if(path.startsWith("/api/v1/dataresources/"))prefix="/api/v1/dataresources/";
        else {chain.doFilter(request,response);return;}
        String id=path.substring(prefix.length()).split("/",2)[0];
        // Collection/admin routes are not dataset mutations. Unknown aliases are
        // resolved/redirected upstream before mutation; canonical requests enter here.
        if(id.isBlank() || !resources.existsById(id)) {chain.doFilter(request,response);return;}
        boolean owner=ownership.findById(id).filter(value->value.getUsername().equalsIgnoreCase(auth.getName())).isPresent();
        boolean staff=auth.getAuthorities().stream().anyMatch(value->java.util.Set.of("ROLE_CURATOR","ROLE_ADMINISTRATOR").contains(value.getAuthority()));
        boolean trustedService=auth instanceof edu.kit.datamanager.security.filter.JwtAuthenticationToken;
        // Do not give an unrelated local user a lease or disclose a busy private
        // dataset. Existing controller/ownership guards produce their normal denial.
        if(!owner && !staff && !trustedService) {chain.doFilter(request,response);return;}
        ResourceMutationCoordinator.Lease lease;
        try {lease=coordinator.acquire(id);}
        catch(ResponseStatusException unavailable) {
            response.setStatus(unavailable.getStatusCode().value());response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control","no-store");response.setHeader("Retry-After","1");
            json.writeValue(response.getWriter(),java.util.Map.of("message",unavailable.getReason()));return;
        }
        try(lease) {chain.doFilter(request,response);}
    }
}
