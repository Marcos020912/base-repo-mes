package edu.kit.datamanager.repo.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import edu.kit.datamanager.repo.domain.LocalUser;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalJwtService {
    private final byte[] secret;
    private final long validityMinutes;

    public LocalJwtService(@Value("${repo.auth.jwtSecret}") String secret,
            @Value("${repo.auth.token-validity-minutes:480}") long validityMinutes) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.validityMinutes = validityMinutes;
        if (this.secret.length < 32) throw new IllegalArgumentException("repo.auth.jwtSecret must contain at least 32 bytes");
    }

    public String create(LocalUser user) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(user.getUsername()).issuer("base-repo")
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(validityMinutes * 60)))
                    .claim("roles", List.of(user.getRole().authority())).build();
            SignedJWT jwt = new SignedJWT(new com.nimbusds.jose.JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception ex) { throw new IllegalStateException("Unable to create access token", ex); }
    }

    public JWTClaimsSet verify(String token) throws ParseException, com.nimbusds.jose.JOSEException {
        SignedJWT jwt = SignedJWT.parse(token);
        if (!jwt.verify(new MACVerifier(secret)) || jwt.getJWTClaimsSet().getExpirationTime().before(new Date())) throw new com.nimbusds.jose.JOSEException("Invalid or expired token");
        return jwt.getJWTClaimsSet();
    }
}
