package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Curated publication metadata; published versions cannot be edited in place. */
@RestController
@RequestMapping("/api/v1/scientific")
public class ScientificRecordController {
    private static final Pattern DOI = Pattern.compile("^10\\.\\d{4,9}/\\S+$");
    private static final Pattern ORCID = Pattern.compile("^(?:https://orcid.org/)?\\d{4}-\\d{4}-\\d{4}-[\\dX]{4}$");
    private static final Pattern ROR = Pattern.compile("^(?:https://ror.org/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}$");
    private final ScientificRecordRepository records;
    private final ResourceOwnershipRepository ownership;
    private final IDataResourceDao resources;

    public ScientificRecordController(ScientificRecordRepository records, ResourceOwnershipRepository ownership, IDataResourceDao resources) {
        this.records = records;
        this.ownership = ownership;
        this.resources = resources;
    }

    @GetMapping("/{id}")
    public ScientificRecord get(@PathVariable String id) {
        requireResource(id);
        ScientificRecord record = records.findById(id).orElseGet(() -> new ScientificRecord(id));
        if (record.getStatus() != PublicationStatus.PUBLISHED) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean owner = ownership.findById(id).filter(item -> item.getUsername().equalsIgnoreCase(username)).isPresent();
            boolean curator = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_CURATOR".equals(authority.getAuthority()) || "ROLE_ADMINISTRATOR".equals(authority.getAuthority()));
            if (!owner && !(curator && record.getStatus() == PublicationStatus.IN_REVIEW)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ficha no disponible.");
            }
        }
        return record;
    }

    @PutMapping("/{id}")
    @Transactional
    public ScientificRecord update(@PathVariable String id, @RequestBody UpdateRequest input) {
        requireOwner(id);
        ScientificRecord record = records.findById(id).orElseGet(() -> new ScientificRecord(id));
        if (record.getStatus() != PublicationStatus.DRAFT) throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede editar un borrador.");
        record.setVersionLabel(clean(input.versionLabel(), 40));
        record.setVersionDoi(validDoi(input.versionDoi()));
        record.setConceptualDoi(validDoi(input.conceptualDoi()));
        record.setLicenseId(clean(input.licenseId(), 100));
        String access = clean(input.accessLevel(), 30);
        if (access != null && !List.of("OPEN", "RESTRICTED", "EMBARGOED").contains(access)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nivel de acceso no válido.");
        record.setAccessLevel(access == null ? "OPEN" : access);
        record.setLanguage(clean(input.language(), 16));
        record.setDiscipline(clean(input.discipline(), 255));
        record.setKeywords(clean(input.keywords(), 2000));
        String orcid = clean(input.orcid(), 255);
        if (orcid != null && !ORCID.matcher(orcid).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ORCID no válido.");
        record.setOrcid(orcid);
        record.setInstitution(clean(input.institution(), 255));
        String ror = clean(input.ror(), 255);
        if (ror != null && !ROR.matcher(ror).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROR no válido.");
        record.setRor(ror);
        record.setRelatedPublications(clean(input.relatedPublications(), 2000));
        record.setMethodology(clean(input.methodology(), 2000));
        return records.save(record);
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public ScientificRecord submit(@PathVariable String id) {
        requireOwner(id);
        ScientificRecord record = records.findById(id).orElseGet(() -> new ScientificRecord(id));
        if (record.getStatus() != PublicationStatus.DRAFT) throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso no está en borrador.");
        if (record.getVersionLabel() == null || record.getLicenseId() == null || record.getInstitution() == null || record.getMethodology() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete versión, licencia, institución y metodología antes de solicitar revisión.");
        }
        record.setStatus(PublicationStatus.IN_REVIEW);
        record.setSubmittedAt(Instant.now());
        return records.save(record);
    }

    @PostMapping("/{id}/derive-from/{previousId}")
    @Transactional
    public ScientificRecord deriveFrom(@PathVariable String id, @PathVariable String previousId) {
        if (id.equals(previousId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva versión debe ser otro recurso.");
        requireOwner(id);
        requireOwner(previousId);
        ScientificRecord current = records.findById(id).orElseGet(() -> new ScientificRecord(id));
        ScientificRecord previous = records.findById(previousId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versión anterior no encontrada."));
        if (current.getStatus() != PublicationStatus.DRAFT || current.getPreviousResourceId() != null || previous.getStatus() != PublicationStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo un borrador nuevo puede derivarse de una versión publicada propia.");
        }
        current.setPreviousResourceId(previousId);
        current.setConceptualDoi(previous.getConceptualDoi());
        return records.save(current);
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public List<ScientificRecord> reviews() {
        return records.findByStatusOrderBySubmittedAtAsc(PublicationStatus.IN_REVIEW);
    }

    @PostMapping("/{id}/return-to-draft")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ScientificRecord returnToDraft(@PathVariable String id) {
        ScientificRecord record = current(id);
        if (record.getStatus() != PublicationStatus.IN_REVIEW) throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso no está en revisión.");
        record.setStatus(PublicationStatus.DRAFT);
        return records.save(record);
    }

    @PostMapping("/{id}/publish")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ScientificRecord publish(@PathVariable String id, @RequestBody PublicationApproval approval) {
        ScientificRecord record = current(id);
        if (record.getStatus() != PublicationStatus.IN_REVIEW) throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso debe pasar por revisión.");
        if (!approval.doiRegisteredExternally()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DOI debe estar registrado antes de publicar; esta aplicación aún no lo registra automáticamente.");
        if (record.getVersionDoi() == null || record.getLicenseId() == null || record.getVersionLabel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOI de versión, licencia y versión son obligatorios.");
        }
        if (records.existsByVersionDoiIgnoreCaseAndResourceIdNot(record.getVersionDoi(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este DOI ya pertenece a otra versión.");
        }
        record.setStatus(PublicationStatus.PUBLISHED);
        record.setPublishedAt(Instant.now());
        return records.save(record);
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ScientificRecord withdraw(@PathVariable String id, @RequestBody WithdrawalRequest input) {
        ScientificRecord record = current(id);
        if (record.getStatus() != PublicationStatus.PUBLISHED && record.getStatus() != PublicationStatus.RESTRICTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede retirar un recurso publicado.");
        }
        record.setWithdrawalReason(clean(input.reason(), 1000));
        if (record.getWithdrawalReason() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo de la retirada.");
        record.setStatus(PublicationStatus.WITHDRAWN);
        record.setWithdrawnAt(Instant.now());
        return records.save(record);
    }

    private ScientificRecord current(String id) {
        requireResource(id);
        return records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ficha científica."));
    }

    private void requireResource(String id) {
        if (!resources.findById(id).isPresent()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado.");
    }

    private void requireOwner(String id) {
        requireResource(id);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (ownership.findById(id).filter(item -> item.getUsername().equalsIgnoreCase(username)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede editar este recurso.");
        }
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un campo supera la longitud permitida.");
        return result;
    }

    private static String validDoi(String value) {
        String doi = clean(value, 255);
        if (doi != null && !DOI.matcher(doi).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOI no válido.");
        return doi;
    }

    public record UpdateRequest(String versionLabel, String versionDoi, String conceptualDoi, String licenseId,
                                String accessLevel, String language, String discipline, String keywords,
                                String orcid, String institution, String ror, String relatedPublications,
                                String methodology) {}
    public record PublicationApproval(boolean doiRegisteredExternally) {}
    public record WithdrawalRequest(String reason) {}
}
