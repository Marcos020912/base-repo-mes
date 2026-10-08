package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.service.OrcidOAuthService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scientific")
public class OrcidOAuthController {
    private final OrcidOAuthService oauth;
    public OrcidOAuthController(OrcidOAuthService oauth) { this.oauth = oauth; }

    @GetMapping("/orcid/status")
    public Map<String, Boolean> status() { return Map.of("enabled", oauth.isEnabled()); }

    @PostMapping("/{id}/creators/{creatorId}/orcid/start")
    public Map<String, String> start(@PathVariable String id, @PathVariable Long creatorId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return Map.of("authorizeUrl", oauth.start(id, creatorId, username));
    }

    @GetMapping("/orcid/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String state,
            @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
        var result = oauth.finish(state, code, error);
        String target = "/resource.html?id=" + URLEncoder.encode(result.resourceId(), StandardCharsets.UTF_8)
                + "&orcid=" + (result.authenticated() ? "authenticated" : "failed");
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, URI.create(target).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer").build();
    }
}
