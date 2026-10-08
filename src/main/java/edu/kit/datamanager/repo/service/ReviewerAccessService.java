package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ReviewerAccessLink;
import edu.kit.datamanager.repo.repository.ReviewerAccessLinkRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Opaque, expiring and revocable capability scoped to one IN_REVIEW deposit. */
@Service
public class ReviewerAccessService {
    private final ReviewerAccessLinkRepository links;
    private final ScientificRecordRepository records;
    private final SecureRandom random = new SecureRandom();

    public ReviewerAccessService(ReviewerAccessLinkRepository links, ScientificRecordRepository records) {
        this.links = links; this.records = records;
    }

    @Transactional
    public CreatedLink create(String resourceId, String actor, int hours) {
        requireReview(resourceId);
        if (hours < 1 || hours > 336) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duración entre 1 y 336 horas.");
        long active = links.findByResourceIdOrderByCreatedAtDesc(resourceId).stream()
                .filter(link -> link.getRevokedAt() == null && link.getExpiresAt().isAfter(Instant.now())).count();
        if (active >= 20) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existen 20 enlaces activos.");
        byte[] entropy = new byte[32]; random.nextBytes(entropy);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        Instant now = Instant.now();
        ReviewerAccessLink link = links.save(new ReviewerAccessLink(resourceId, digest(token), actor, now, now.plusSeconds(hours * 3600L)));
        return new CreatedLink(link.getId(), link.getExpiresAt(), "/review-access.html#token=" + token);
    }

    @Transactional(readOnly = true)
    public List<LinkSummary> list(String resourceId) {
        requireReview(resourceId);
        return links.findByResourceIdOrderByCreatedAtDesc(resourceId).stream()
                .map(link -> new LinkSummary(link.getId(), link.getCreatedBy(), link.getCreatedAt(),
                        link.getExpiresAt(), link.getRevokedAt())).toList();
    }

    @Transactional
    public void revoke(String resourceId, long linkId) {
        requireReview(resourceId);
        ReviewerAccessLink link = links.findById(linkId)
                .filter(item -> resourceId.equals(item.getResourceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (link.getRevokedAt() == null) { link.setRevokedAt(Instant.now()); links.save(link); }
    }

    @Transactional(readOnly = true)
    public String requireValid(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ReviewerAccessLink link = links.findByTokenHash(digest(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (link.getRevokedAt() != null || !link.getExpiresAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        requireReview(link.getResourceId());
        return link.getResourceId();
    }

    private void requireReview(String resourceId) {
        var record = records.findById(resourceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getStatus() != PublicationStatus.IN_REVIEW) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    public record CreatedLink(Long id, Instant expiresAt, String relativeUrl) {}
    public record LinkSummary(Long id, String createdBy, Instant createdAt, Instant expiresAt, Instant revokedAt) {}
}
