package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import edu.kit.datamanager.repo.service.ScientificQualityService;
import edu.kit.datamanager.repo.service.ScientificVocabularyService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock;
    private static final Pattern DOI = Pattern.compile("^10\\.\\d{4,9}/\\S+$");
    private static final Pattern ORCID = Pattern.compile("^(?:https://orcid.org/)?\\d{4}-\\d{4}-\\d{4}-[\\dX]{4}$");
    private static final Pattern ROR = Pattern.compile("^(?:https://ror.org/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}$");
    private final ScientificRecordRepository records;
    private final ResourceOwnershipRepository ownership;
    private final IDataResourceDao resources;
    private final ScientificQualityService quality;
    private final ScientificRecordEventRepository events;
    @Autowired(required = false)
    private ScientificVocabularyService vocabularies;
    @Value("${repo.datacite.enabled:false}")
    private boolean automatedDoiEnabled;
    @Autowired(required=false) private edu.kit.datamanager.repo.service.ScientificPrivacyService privacy;

    public ScientificRecordController(ScientificRecordRepository records, ResourceOwnershipRepository ownership, IDataResourceDao resources, ScientificQualityService quality, ScientificRecordEventRepository events, edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock) {
        this.writeLock = writeLock;
        this.records = records;
        this.ownership = ownership;
        this.resources = resources;
        this.quality = quality;
        this.events = events;
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

    @Autowired(required=false)
    private edu.kit.datamanager.repo.service.ScientificMetadataProfileService metadataProfiles;

    @org.springframework.web.bind.annotation.ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<?> concurrentScientificWrite(){return ResponseEntity.status(409).body(Map.of("message","La ficha cambió; recargue antes de guardar."));}

    @PutMapping("/{id}/metadata-profile")
    @Transactional
    public ScientificRecord applyMetadataProfile(@PathVariable String id,@RequestBody MetadataProfileApplication input){
        requireOwner(id);
        writeLock.acquire(id);
        ScientificRecord saved=metadataProfiles.apply(id,input.profileId(),input.profileRevision(),input.resourceRevision());
        audit(id,"METADATA_PROFILE_APPLIED",saved.getMetadataProfileId());
        return saved;
    }
    @io.swagger.v3.oas.annotations.media.Schema(name="MetadataProfileApplication")
    public record MetadataProfileApplication(String profileId,Long profileRevision,Long resourceRevision){}

    @PutMapping("/{id}")
    @Transactional
    public ScientificRecord update(@PathVariable String id, @RequestBody UpdateRequest input) {
        requireOwner(id);
        writeLock.acquire(id);
        ScientificRecord record = records.findById(id).orElseGet(() -> new ScientificRecord(id));
        if (record.getStatus() != PublicationStatus.DRAFT) throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede editar un borrador.");
        record.setVersionLabel(clean(input.versionLabel(), 40));
        if (automatedDoiEnabled) {
            if (validDoi(input.versionDoi()) != null && !input.versionDoi().equalsIgnoreCase(record.getVersionDoi() == null ? "" : record.getVersionDoi()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El DOI de versión lo asigna DataCite; use Reservar DOI.");
            if (validDoi(input.conceptualDoi()) != null && !input.conceptualDoi().equalsIgnoreCase(record.getConceptualDoi() == null ? "" : record.getConceptualDoi()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El DOI conceptual lo asigna DataCite.");
        } else {
            record.setVersionDoi(validDoi(input.versionDoi()));
            record.setConceptualDoi(validDoi(input.conceptualDoi()));
        }
        String license = clean(input.licenseId(), 100);
        if (vocabularies != null && !vocabularies.validLicense(license, record.getLicenseId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La licencia no pertenece al vocabulario configurado.");
        record.setLicenseId(license);
        String access = clean(input.accessLevel(), 30);
        if (access != null && !List.of("OPEN", "RESTRICTED", "EMBARGOED").contains(access)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nivel de acceso no válido.");
        record.setAccessLevel(access == null ? "OPEN" : access);
        try { record.setEmbargoUntil(input.embargoUntil() == null || input.embargoUntil().isBlank() ? null : Instant.parse(input.embargoUntil())); }
        catch (java.time.format.DateTimeParseException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha de embargo no válida; use UTC ISO 8601."); }
        if ("EMBARGOED".equals(record.getAccessLevel()) && record.getEmbargoUntil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la fecha de fin del embargo.");
        }
        record.setLanguage(clean(input.language(), 16));
        String discipline = clean(input.discipline(), 255);
        if (vocabularies != null && !vocabularies.validDiscipline(discipline, record.getDiscipline()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La disciplina no pertenece al vocabulario configurado.");
        record.setDiscipline(discipline);
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
        if(input.productionDescription()!=null)record.setProductionDescription(clean(input.productionDescription(),5000));
        if(input.processingDescription()!=null)record.setProcessingDescription(clean(input.processingDescription(),5000));
        if(input.processingTools()!=null)record.setProcessingTools(clean(input.processingTools(),2000));
        if(input.summary()!=null)record.setSummary(clean(input.summary(),5000));
        if(input.geographicCoverage()!=null)record.setGeographicCoverage(clean(input.geographicCoverage(),1000));
        var start=input.temporalStart()==null?record.getTemporalStart():coverageDate(input.temporalStart());
        var end=input.temporalEnd()==null?record.getTemporalEnd():coverageDate(input.temporalEnd());
        if(start!=null && end!=null && start.isAfter(end))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El inicio de cobertura no puede ser posterior al fin.");
        record.setTemporalStart(start);record.setTemporalEnd(end);
        if(input.translations()!=null) {
            var validated=validateTranslations(input.translations());
            record.getTranslations().clear();record.getTranslations().putAll(validated);
        }
        ScientificRecord saved = records.save(record);
        audit(id, "METADATA_UPDATED", null);
        return saved;
    }

    @PostMapping("/{id}/submit")
    @Transactional
    public ScientificRecord submit(@PathVariable String id) {
        requireOwner(id);
        writeLock.acquire(id);
        ScientificRecord record = records.findById(id).orElseGet(() -> new ScientificRecord(id));
        if (record.getStatus() != PublicationStatus.DRAFT) throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso no está en borrador.");
        if(privacy!=null)privacy.requireSubmissionAllowed(id,record.getAccessLevel());
        var report = quality.inspect(record);
        if (!report.blockers().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Faltan datos para revisión: " + String.join(", ", report.blockers()));
        record.setStatus(PublicationStatus.IN_REVIEW);
        record.setSubmittedAt(Instant.now());
        ScientificRecord saved = records.save(record);
        audit(id, "SUBMITTED", null);
        return saved;
    }

    @GetMapping("/{id}/quality")
    @Transactional(readOnly = true)
    public ScientificQualityService.QualityReport quality(@PathVariable String id) {
        ScientificRecord record = get(id);
        return quality.inspect(record);
    }

    @PostMapping("/{id}/derive-from/{previousId}")
    @Transactional
    public ScientificRecord deriveFrom(@PathVariable String id, @PathVariable String previousId) {
        if (id.equals(previousId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva versión debe ser otro recurso.");
        requireOwner(id);
        requireOwner(previousId);
        for (String resourceId : java.util.stream.Stream.of(id, previousId).sorted().toList()) writeLock.acquire(resourceId);
        ScientificRecord current = records.findById(id).orElseGet(() -> new ScientificRecord(id));
        ScientificRecord previous = records.findById(previousId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versión anterior no encontrada."));
        if (current.getStatus() != PublicationStatus.DRAFT || current.getPreviousResourceId() != null || previous.getStatus() != PublicationStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo un borrador nuevo puede derivarse de una versión publicada propia.");
        }
        current.setPreviousResourceId(previousId);
        current.setConceptualDoi(previous.getConceptualDoi());
        current.setMetadataProfileId(previous.getMetadataProfileId());
        current.setMetadataProfileName(previous.getMetadataProfileName());
        current.setMetadataProfileRevision(previous.getMetadataProfileRevision());
        current.setMetadataProfileRequiredFields(new java.util.LinkedHashSet<>(previous.getMetadataProfileRequiredFields()));
        ScientificRecord saved = records.save(current);
        audit(id, "VERSION_DERIVED", previousId);
        return saved;
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public List<ScientificRecord> reviews() {
        return records.findByStatusOrderBySubmittedAtAsc(PublicationStatus.IN_REVIEW);
    }

    @GetMapping("/{id}/history")
    @Transactional(readOnly = true)
    public List<ScientificRecordEvent> history(@PathVariable String id) {
        requireResource(id);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean owner = ownership.findById(id).filter(item -> item.getUsername().equalsIgnoreCase(username)).isPresent();
        boolean curator = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CURATOR".equals(authority.getAuthority()) || "ROLE_ADMINISTRATOR".equals(authority.getAuthority()));
        if (!owner && !curator) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Historial reservado al autor y a curación.");
        return events.findByResourceIdOrderByCreatedAtAscIdAsc(id);
    }

    @PostMapping("/{id}/return-to-draft")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ScientificRecord returnToDraft(@PathVariable String id) {
        writeLock.acquire(id);
        ScientificRecord record = current(id);
        if (record.getStatus() != PublicationStatus.IN_REVIEW) throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso no está en revisión.");
        if(privacy!=null)privacy.clearReview(id);
        record.setStatus(PublicationStatus.DRAFT);
        ScientificRecord saved = records.save(record);
        audit(id, "RETURNED_TO_DRAFT", null);
        return saved;
    }

    @PostMapping("/{id}/publish")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ScientificRecord publish(@PathVariable String id, @RequestBody PublicationApproval approval) {
        if (automatedDoiEnabled) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Use la publicación DataCite del flujo editorial; la confirmación manual está desactivada.");
        writeLock.acquire(id);
        ScientificRecord record = current(id);
        if (record.getStatus() != PublicationStatus.IN_REVIEW) throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso debe pasar por revisión.");
        if(privacy!=null)privacy.requirePublicationAllowed(id,record.getAccessLevel());
        if (!approval.doiRegisteredExternally()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DOI debe estar registrado antes de publicar; esta aplicación aún no lo registra automáticamente.");
        if (record.getVersionDoi() == null || record.getLicenseId() == null || record.getVersionLabel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOI de versión, licencia y versión son obligatorios.");
        }
        if (records.existsByVersionDoiIgnoreCaseAndResourceIdNot(record.getVersionDoi(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este DOI ya pertenece a otra versión.");
        }
        if(record.getMetadataProfileRequiredFields().stream().anyMatch(field->!edu.kit.datamanager.repo.service.ScientificMetadataProfileService.complete(record,field)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Complete los campos requeridos por el perfil aplicado antes de publicar.");
        record.setStatus(PublicationStatus.PUBLISHED);
        record.setPublishedAt(Instant.now());
        ScientificRecord saved = records.save(record);
        audit(id, "PUBLISHED", record.getVersionDoi());
        return saved;
    }

    @PostMapping("/{id}/withdraw")
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ScientificRecord withdraw(@PathVariable String id, @RequestBody WithdrawalRequest input) {
        writeLock.acquire(id);
        ScientificRecord record = current(id);
        if (record.getStatus() != PublicationStatus.PUBLISHED && record.getStatus() != PublicationStatus.RESTRICTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede retirar un recurso publicado.");
        }
        record.setWithdrawalReason(clean(input.reason(), 1000));
        if (record.getWithdrawalReason() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo de la retirada.");
        record.setStatus(PublicationStatus.WITHDRAWN);
        record.setWithdrawnAt(Instant.now());
        ScientificRecord saved = records.save(record);
        audit(id, "WITHDRAWN", record.getWithdrawalReason());
        return saved;
    }

    private ScientificRecord current(String id) {
        requireResource(id);
        return records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ficha científica."));
    }

    private void audit(String id, String action, String detail) {
        events.save(new ScientificRecordEvent(id, SecurityContextHolder.getContext().getAuthentication().getName(), action, detail));
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

    private static java.time.LocalDate coverageDate(String value) {
        if(value.isBlank())return null;
        try {return java.time.LocalDate.parse(value);}
        catch(java.time.format.DateTimeParseException failure) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cobertura temporal no válida; use AAAA-MM-DD.");}
    }
    private static Map<String,edu.kit.datamanager.repo.domain.LocalizedScientificMetadata> validateTranslations(
            Map<String,edu.kit.datamanager.repo.domain.LocalizedScientificMetadata> input) {
        if(input.size()>10)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Máximo diez traducciones por versión.");
        var result=new java.util.LinkedHashMap<String,edu.kit.datamanager.repo.domain.LocalizedScientificMetadata>();
        input.forEach((language,translation)->{
            if(language==null || !language.matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,3}") || translation==null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idioma o traducción no válido.");
            String tag=java.util.Locale.forLanguageTag(language).toLanguageTag();
            if("und".equals(tag) || result.containsKey(tag))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idioma duplicado o no válido.");
            String title=clean(translation.getTitle(),500),summary=clean(translation.getSummary(),5000);
            if(title==null && summary==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cada traducción debe incluir título o resumen.");
            result.put(tag,new edu.kit.datamanager.repo.domain.LocalizedScientificMetadata(title,summary));
        });
        return result;
    }

    @io.swagger.v3.oas.annotations.media.Schema(name="ScientificRecordUpdate")
    public record UpdateRequest(String versionLabel, String versionDoi, String conceptualDoi, String licenseId,
                                String accessLevel, String embargoUntil, String language, String discipline, String keywords,
                                String orcid, String institution, String ror, String relatedPublications,
                                String methodology,String summary,String temporalStart,String temporalEnd,String geographicCoverage,
                                Map<String,edu.kit.datamanager.repo.domain.LocalizedScientificMetadata> translations,
                                String productionDescription,String processingDescription,String processingTools) {
        public UpdateRequest(String versionLabel,String versionDoi,String conceptualDoi,String licenseId,
                String accessLevel,String embargoUntil,String language,String discipline,String keywords,
                String orcid,String institution,String ror,String relatedPublications,String methodology,
                String summary,String temporalStart,String temporalEnd,String geographicCoverage,
                Map<String,edu.kit.datamanager.repo.domain.LocalizedScientificMetadata> translations) {
            this(versionLabel,versionDoi,conceptualDoi,licenseId,accessLevel,embargoUntil,language,discipline,keywords,
                orcid,institution,ror,relatedPublications,methodology,summary,temporalStart,temporalEnd,geographicCoverage,translations,null,null,null);
        }
        /** Preserve the source-level contract used by existing callers; omitted additions keep their values. */
        public UpdateRequest(String versionLabel,String versionDoi,String conceptualDoi,String licenseId,
                String accessLevel,String embargoUntil,String language,String discipline,String keywords,
                String orcid,String institution,String ror,String relatedPublications,String methodology) {
            this(versionLabel,versionDoi,conceptualDoi,licenseId,accessLevel,embargoUntil,language,discipline,keywords,
                orcid,institution,ror,relatedPublications,methodology,null,null,null,null,null);
        }
    }
    public record PublicationApproval(boolean doiRegisteredExternally) {}
    public record WithdrawalRequest(String reason) {}
}
