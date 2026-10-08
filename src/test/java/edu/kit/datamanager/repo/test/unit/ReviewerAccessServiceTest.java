package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ReviewerAccessLink;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ReviewerAccessLinkRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.ReviewerAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ReviewerAccessServiceTest {
    @Test public void linkIsHashedExpiresAndStopsWhenReviewEnds() {
        ReviewerAccessLinkRepository links = mock(ReviewerAccessLinkRepository.class);
        ScientificRecordRepository records = mock(ScientificRecordRepository.class);
        ScientificRecord record = new ScientificRecord("r1"); record.setStatus(PublicationStatus.IN_REVIEW);
        when(records.findById("r1")).thenReturn(Optional.of(record));
        when(links.findByResourceIdOrderByCreatedAtDesc("r1")).thenReturn(List.of());
        java.util.concurrent.atomic.AtomicReference<ReviewerAccessLink> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(links.save(any())).thenAnswer(call -> { ReviewerAccessLink link = call.getArgument(0); link.setId(7L); saved.set(link); return link; });
        when(links.findByTokenHash(any())).thenAnswer(call -> saved.get() != null && saved.get().getTokenHash().equals(call.getArgument(0))
                ? Optional.of(saved.get()) : Optional.empty());
        ReviewerAccessService service = new ReviewerAccessService(links, records);
        assertThrows(ResponseStatusException.class, () -> service.create("r1", "curator", 337));
        var created = service.create("r1", "curator", 48);
        String token = created.relativeUrl().substring(created.relativeUrl().indexOf("#token=") + 7);
        assertEquals(43, token.length());
        assertEquals(64, saved.get().getTokenHash().length());
        assertFalse(saved.get().getTokenHash().contains(token));
        assertEquals("r1", service.requireValid(token));
        assertThrows(ResponseStatusException.class, () -> service.requireValid("bad-token"));
        saved.get().setExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(ResponseStatusException.class, () -> service.requireValid(token));
        saved.get().setExpiresAt(Instant.now().plusSeconds(3600));
        when(links.findById(7L)).thenReturn(Optional.of(saved.get()));
        service.revoke("r1", 7L);
        assertThrows(ResponseStatusException.class, () -> service.requireValid(token));
        saved.get().setRevokedAt(null);
        record.setStatus(PublicationStatus.PUBLISHED);
        assertThrows(ResponseStatusException.class, () -> service.requireValid(token));
    }
}
