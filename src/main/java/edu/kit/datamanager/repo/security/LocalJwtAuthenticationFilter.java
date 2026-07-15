package edu.kit.datamanager.repo.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalJwtAuthenticationFilter extends OncePerRequestFilter {
    private final LocalJwtService tokens;
    public LocalJwtAuthenticationFilter(LocalJwtService tokens) { this.tokens = tokens; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JWTClaimsSet claims = tokens.verify(header.substring(7));
                Collection<String> roles = claims.getStringListClaim("roles");
                var authorities = roles == null ? Collections.<SimpleGrantedAuthority>emptyList() : roles.stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
            } catch (ParseException | com.nimbusds.jose.JOSEException ex) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token"); return; }
        }
        chain.doFilter(request, response);
    }
}
