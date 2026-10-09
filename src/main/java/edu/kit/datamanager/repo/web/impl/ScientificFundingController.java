package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificFunding;
import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificFundingRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Funding references are curated independently of the legacy free-text field. */
@RestController
@RequestMapping("/api/v1/scientific/{id}/funding")
public class ScientificFundingController {
    private final edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock;
    private final ScientificFundingRepository funding;
    private final ScientificRecordRepository records;
    private final ResourceOwnershipRepository ownership;
    private final ScientificRecordEventRepository events;

    public ScientificFundingController(ScientificFundingRepository funding, ScientificRecordRepository records,
            ResourceOwnershipRepository ownership, ScientificRecordEventRepository events, edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock) {
        this.writeLock = writeLock;
        this.funding = funding; this.records = records; this.ownership = ownership; this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ScientificFunding> list(@PathVariable String id) {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getStatus() != PublicationStatus.PUBLISHED && !isOwnerOrCurator(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return funding.findByResourceIdOrderByIdAsc(id);
    }

    @PutMapping
    @Transactional
    public List<ScientificFunding> replace(@PathVariable String id, @RequestBody List<FundingInput> input) {
        if (!isOwner(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede editar financiación.");
        writeLock.acquire(id);
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getStatus() != PublicationStatus.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede editar un borrador.");
        if (input == null || input.size() > 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se admiten hasta 20 financiaciones.");
        var unique = new HashSet<String>();
        var values = input.stream().map(item -> {
            if (item == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Financiación no válida.");
            String funder = clean(item.funderName(), 255);
            String ror = clean(item.funderRor(), 255);
            String award = clean(item.awardNumber(), 100);
            String title = clean(item.awardTitle(), 500);
            if (funder == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el financiador.");
            if (ror != null && !ror.matches("(?:https://ror.org/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROR del financiador no válido.");
            String key = funder.toLowerCase(java.util.Locale.ROOT) + ":"
                    + (award == null ? "" : award.toLowerCase(java.util.Locale.ROOT)) + ":"
                    + (title == null ? "" : title.toLowerCase(java.util.Locale.ROOT));
            if (!unique.add(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Financiación duplicada.");
            return new ScientificFunding(id, funder, ror, award, title);
        }).toList();
        funding.deleteByResourceId(id);
        var saved = funding.saveAll(values);
        events.save(new ScientificRecordEvent(id, username(), "FUNDING_UPDATED", Integer.toString(saved.size())));
        return saved;
    }

    private boolean isOwnerOrCurator(String id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return isOwner(id) || auth != null && auth.getAuthorities().stream().anyMatch(item ->
                "ROLE_CURATOR".equals(item.getAuthority()) || "ROLE_ADMINISTRATOR".equals(item.getAuthority()));
    }

    private boolean isOwner(String id) {
        String user = username();
        return user != null && ownership.findById(id)
                .filter(item -> item.getUsername().equalsIgnoreCase(user)).isPresent();
    }

    private String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor demasiado largo.");
        return clean;
    }

    public record FundingInput(String funderName, String funderRor, String awardNumber, String awardTitle) {}
}
