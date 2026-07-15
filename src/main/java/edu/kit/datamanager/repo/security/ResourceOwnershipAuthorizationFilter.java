package edu.kit.datamanager.repo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
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
    private final ObjectMapper json = new ObjectMapper();
    public ResourceOwnershipAuthorizationFilter(ResourceOwnershipRepository ownership) { this.ownership = ownership; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI(); String method = request.getMethod();
        if (!path.startsWith("/api/v1/dataresources/")) return true;
        return !(HttpMethod.PUT.matches(method) || HttpMethod.PATCH.matches(method) || HttpMethod.DELETE.matches(method)
                || (HttpMethod.POST.matches(method) && (path.contains("/data/") || path.endsWith("/description") || path.endsWith("/attachments"))));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String remainder = request.getRequestURI().substring("/api/v1/dataresources/".length());
        String resourceId = remainder.split("/", 2)[0];
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (ownership.findById(resourceId).filter(item -> item.getUsername().equalsIgnoreCase(username)).isEmpty()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); response.setContentType("application/json");
            json.writeValue(response.getWriter(), java.util.Map.of("message", "Solo el autor del recurso puede modificarlo.")); return;
        }
        chain.doFilter(request, response);
    }
}
