package edu.kit.datamanager.repo.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import edu.kit.datamanager.security.filter.JwtAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalJwtAuthenticationFilter extends OncePerRequestFilter {
    private final LocalJwtService tokens;
    private final LocalUserRepository users;
    private final boolean allowLegacy;
    public LocalJwtAuthenticationFilter(LocalJwtService tokens, LocalUserRepository users,
                                        @Value("${repo.auth.allow-legacy-jwt:false}") boolean allowLegacy) {
        this.tokens = tokens; this.users = users; this.allowLegacy = allowLegacy;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Only handle tokens issued by the local login endpoint. Other
                // tokens retain their legacy verification path and ACL rules.
                if (!"base-repo".equals(SignedJWT.parse(header.substring(7)).getJWTClaimsSet().getIssuer())) {
                    if (allowLegacy) {
                        var legacyClaims = tokens.verifyLegacy(header.substring(7));
                        SecurityContextHolder.getContext().setAuthentication(JwtAuthenticationToken.factoryToken(header.substring(7), legacyClaims));
                    }
                    chain.doFilter(request, response); return;
                }
                JWTClaimsSet claims = tokens.verify(header.substring(7));
                LocalUser user = users.findByUsernameIgnoreCase(claims.getSubject()).orElse(null);
                if (user == null || !user.isEnabled() || !user.isVerified()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account unavailable"); return;
                }
                Long passwordVersion = claims.getLongClaim("passwordVersion");
                long currentVersion = user.getPasswordChangedAt() == null ? 0L : user.getPasswordChangedAt().toEpochMilli();
                // JWT iat has second precision; compare signed credential version for new tokens.
                // Tokens issued before this claim existed retain the conservative timestamp check.
                if (passwordVersion != null ? passwordVersion.longValue() != currentVersion :
                        user.getPasswordChangedAt() != null && (claims.getIssueTime() == null ||
                        claims.getIssueTime().toInstant().isBefore(user.getPasswordChangedAt()))) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired after password change"); return;
                }
                var authorities = java.util.List.of(new SimpleGrantedAuthority(user.getRole().authority()));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities));
            } catch (ParseException | com.nimbusds.jose.JOSEException | io.jsonwebtoken.JwtException | IllegalArgumentException ex) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token"); return; }
        }
        chain.doFilter(request, response);
    }
}
