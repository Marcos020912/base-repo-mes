package edu.kit.datamanager.repo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Restricts resource mutations to the user recorded as its author. */
@Component
public class ResourceOwnershipAuthorizationFilter extends OncePerRequestFilter {
    private final ResourceOwnershipRepository ownership;
    private final ScientificRecordRepository scientificRecords;
    private final ObjectMapper json = new ObjectMapper();
    public ResourceOwnershipAuthorizationFilter(ResourceOwnershipRepository ownership, ScientificRecordRepository scientificRecords) {
        this.ownership = ownership;
        this.scientificRecords = scientificRecords;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI(); String method = request.getMethod();
        if (!path.startsWith("/api/v1/dataresources/")) return true;
        return !(HttpMethod.PUT.matches(method) || HttpMethod.PATCH.matches(method) || HttpMethod.DELETE.matches(method)
                || (HttpMethod.POST.matches(method) && (path.contains("/data/") || path.endsWith("/description") || path.endsWith("/attachments"))));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String remainder = request.getRequestURI().substring("/api/v1/dataresources/".length());
        String resourceId = remainder.split("/", 2)[0];
        if (SecurityContextHolder.getContext().getAuthentication() == null) { chain.doFilter(request, response); return; }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        PublicationStatus status = scientificRecords.findById(resourceId).map(record -> record.getStatus()).orElse(PublicationStatus.DRAFT);
        if (status != PublicationStatus.DRAFT) {
            response.setStatus(HttpServletResponse.SC_CONFLICT); response.setContentType("application/json");
            json.writeValue(response.getWriter(), java.util.Map.of("message", "El recurso no es un borrador editable. Una versión publicada no puede modificarse ni eliminarse; solicite su retirada.")); return;
        }
        // Existing trusted-service identities remain governed by upstream
        // DataResource ACLs. Local login issues UsernamePassword tokens.
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            chain.doFilter(request, response); return;
        }
        boolean administrator = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMINISTRATOR".equals(authority.getAuthority()));
        boolean owner = ownership.findById(resourceId).filter(item -> item.getUsername().equalsIgnoreCase(username)).isPresent();
        if (!owner && !(administrator && HttpMethod.DELETE.matches(request.getMethod()) && remainder.indexOf('/') < 0)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); response.setContentType("application/json");
            json.writeValue(response.getWriter(), java.util.Map.of("message", "Solo el autor del recurso puede modificarlo.")); return;
        }
        chain.doFilter(request, response);
    }
}
