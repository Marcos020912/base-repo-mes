package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DoiRegistration;
import edu.kit.datamanager.repo.domain.DoiSyncEvent;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import edu.kit.datamanager.repo.repository.DoiRegistrationRepository;
import edu.kit.datamanager.repo.repository.DoiSyncEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.ScientificRelationRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificFundingRepository;
import edu.kit.datamanager.repo.repository.ScientificAffiliationRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Idempotent, recoverable boundary between PostgreSQL editorial state and DataCite. */
@Service
public class DoiWorkflowService {
    private final DataCiteService datacite;
    private final DataCiteMetadataMapper mapper;
    private final DoiRegistrationRepository registrations;
    private final DoiSyncEventRepository syncEvents;
    private final ScientificRecordRepository records;
    private final ScientificRecordEventRepository editorialEvents;
    private final IDataResourceDao resources;
    private final ScientificQualityService quality;
    private final ScientificRelationRepository relations;
    private final ScientificCreatorRepository creators;
    private final ScientificFundingRepository funding;
    private final ScientificAffiliationRepository affiliations;
    private final TransactionTemplate transactions;
    private final String publicBaseUrl;

    public DoiWorkflowService(DataCiteService datacite, DataCiteMetadataMapper mapper,
            DoiRegistrationRepository registrations, DoiSyncEventRepository syncEvents,
            ScientificRecordRepository records, ScientificRecordEventRepository editorialEvents,
            IDataResourceDao resources, ScientificQualityService quality, ScientificRelationRepository relations,
            ScientificCreatorRepository creators, ScientificFundingRepository funding,
            ScientificAffiliationRepository affiliations,
            org.springframework.transaction.PlatformTransactionManager manager,
            @Value("${repo.datacite.public-base-url:}") String publicBaseUrl) {
        this.datacite = datacite;
        this.mapper = mapper;
        this.registrations = registrations;
        this.syncEvents = syncEvents;
        this.records = records;
        this.editorialEvents = editorialEvents;
        this.resources = resources;
        this.quality = quality;
        this.relations = relations;
        this.creators = creators;
        this.funding = funding;
        this.affiliations = affiliations;
        this.transactions = new TransactionTemplate(manager);
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean enabled() { return datacite.isEnabled(); }

    /** Both intents commit before any remote call, so an interrupted request can resume. */
    public DoiStatus reserve(String resourceId) {
        requireEnabled();
        // Do not create permanent external identifiers without a resolvable landing URL configuration.
        landing(resourceId);
        ScientificRecord science = science(resourceId);
        if (science.getStatus() != PublicationStatus.DRAFT && science.getStatus() != PublicationStatus.IN_REVIEW)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo un borrador o depósito en revisión puede reservar DOI.");
        String rootId = root(resourceId);
        conceptLanding(rootId);
        String conceptDoi = generatedDoi("c", rootId);
        String versionDoi = generatedDoi("v", resourceId);
        if (science.getVersionDoi() != null && !science.getVersionDoi().equalsIgnoreCase(versionDoi))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso ya tiene un DOI manual; necesita conciliación antes de automatizar.");
        if (science.getConceptualDoi() != null && !science.getConceptualDoi().equalsIgnoreCase(conceptDoi))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El DOI conceptual existente necesita conciliación.");
        transactions.executeWithoutResult(status -> {
            ensureRegistration("c:" + rootId, rootId, "CONCEPT", conceptDoi);
            ensureRegistration("v:" + resourceId, resourceId, "VERSION", versionDoi);
        });
        reconcileDraft("c:" + rootId, conceptDoi);
        reconcileDraft("v:" + resourceId, versionDoi);
        transactions.executeWithoutResult(status -> {
            ScientificRecord current = science(resourceId);
            if (current.getStatus() != PublicationStatus.DRAFT && current.getStatus() != PublicationStatus.IN_REVIEW)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El depósito cambió de estado durante la reserva.");
            current.setConceptualDoi(conceptDoi);
            current.setVersionDoi(versionDoi);
            records.save(current);
        });
        return status(resourceId);
    }

    public ScientificRecord publish(String resourceId, String actor) {
        requireEnabled();
        ScientificRecord science = science(resourceId);
        if (science.getStatus() == PublicationStatus.PUBLISHED) return science;
        if (science.getStatus() != PublicationStatus.IN_REVIEW)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El recurso debe pasar por revisión.");
        var report = transactions.execute(status -> quality.inspect(science(resourceId)));
        if (!report.blockers().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan datos para publicar: " + String.join(", ", report.blockers()));
        DoiStatus ids = reserve(resourceId);
        String rootId = ids.rootResourceId();
        URI versionLanding = landing(resourceId);
        Map<String, Object> version = new LinkedHashMap<>(transactions.execute(status -> {
            var resource = resources.findById(resourceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado."));
            return mapper.version(resource, science(resourceId), versionLanding, creators.findByResourceId(resourceId),
                    funding.findByResourceIdOrderByIdAsc(resourceId),
                    affiliations.findByResourceIdOrderByCreatorIdAscSortOrderAsc(resourceId));
        }));
        List<Map<String, String>> versionRelations = new ArrayList<>();
        versionRelations.add(relation(ids.conceptualDoi(), "IsVersionOf"));
        if (science.getPreviousResourceId() != null) {
            ScientificRecord previous = science(science.getPreviousResourceId());
            if (previous.getStatus() != PublicationStatus.PUBLISHED || previous.getVersionDoi() == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La versión anterior debe estar publicada con DOI.");
            if(!managedRegistration("v:"+previous.getResourceId()).getDoi().equalsIgnoreCase(previous.getVersionDoi())
                    || !ids.conceptualDoi().equalsIgnoreCase(previous.getConceptualDoi()==null?"":previous.getConceptualDoi()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Los DOI de la versión anterior requieren conciliación.");
            versionRelations.add(relation(previous.getVersionDoi(), "IsNewVersionOf"));
        }
        relations.findByResourceIdOrderByIdAsc(resourceId).forEach(item -> versionRelations.add(Map.of(
                "relatedIdentifier", item.getIdentifier(),
                "relatedIdentifierType", item.getIdentifierType().name(),
                "relationType", item.getRelationType().name())));
        version.put("relatedIdentifiers", versionRelations);

        Map<String, Object> concept = new LinkedHashMap<>(transactions.execute(status -> {
            var resource = resources.findById(resourceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado."));
            return mapper.version(resource, science(resourceId), conceptLanding(rootId), creators.findByResourceId(resourceId),
                    funding.findByResourceIdOrderByIdAsc(resourceId),
                    affiliations.findByResourceIdOrderByCreatorIdAscSortOrderAsc(resourceId));
        }));
        concept.remove("version");
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        records.findByConceptualDoiIgnoreCase(ids.conceptualDoi()).stream()
                .filter(item -> item.getStatus() == PublicationStatus.PUBLISHED || item.getStatus() == PublicationStatus.WITHDRAWN)
                .forEach(item -> { if (item.getVersionDoi() != null) versions.add(item.getVersionDoi()); });
        versions.add(ids.versionDoi());
        concept.put("relatedIdentifiers", versions.stream().map(doi -> relation(doi, "HasVersion")).toList());

        publishRemote("c:" + rootId, ids.conceptualDoi(), concept);
        publishRemote("v:" + resourceId, ids.versionDoi(), version);
        if(science.getPreviousResourceId()!=null) synchronizePrevious(science.getPreviousResourceId(),ids.versionDoi());
        return transactions.execute(status -> {
            ScientificRecord current = science(resourceId);
            if (current.getStatus() == PublicationStatus.PUBLISHED) return current;
            if (current.getStatus() != PublicationStatus.IN_REVIEW)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El depósito cambió de estado durante la publicación.");
            current.setStatus(PublicationStatus.PUBLISHED);
            current.setPublishedAt(Instant.now());
            ScientificRecord saved = records.save(current);
            editorialEvents.save(new ScientificRecordEvent(resourceId, actor, "PUBLISHED", ids.versionDoi()));
            return saved;
        });
    }

    /** Update only relations: published files and all other remote metadata remain untouched. */
    private void synchronizePrevious(String previousId,String successorDoi) {
        ScientificRecord previous=science(previousId);
        DoiRegistration registration=managedRegistration("v:"+previousId);
        List<Map<String,String>> identifiers=new ArrayList<>();
        identifiers.add(relation(previous.getConceptualDoi(),"IsVersionOf"));
        if(previous.getPreviousResourceId()!=null) {
            String ancestorDoi=science(previous.getPreviousResourceId()).getVersionDoi();
            if(ancestorDoi!=null)identifiers.add(relation(ancestorDoi,"IsNewVersionOf"));
        }
        var successors=new LinkedHashSet<String>();
        records.findByPreviousResourceId(previousId).stream()
            .filter(item->item.getStatus()==PublicationStatus.PUBLISHED || item.getStatus()==PublicationStatus.WITHDRAWN)
            .forEach(item->{if(item.getVersionDoi()!=null)successors.add(item.getVersionDoi());});
        successors.add(successorDoi);
        successors.forEach(doi->identifiers.add(relation(doi,"IsPreviousVersionOf")));
        relations.findByResourceIdOrderByIdAsc(previousId).forEach(item->identifiers.add(Map.of(
            "relatedIdentifier",item.getIdentifier(),"relatedIdentifierType",item.getIdentifierType().name(),"relationType",item.getRelationType().name())));
        try {
            var updated=datacite.updateMetadata(registration.getDoi(),Map.of("relatedIdentifiers",identifiers));
            if(!"findable".equalsIgnoreCase(updated.state()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"DataCite no confirmó relaciones de la versión anterior.");
            recordSync("v:"+previousId,"UPDATE_RELATIONS","FINDABLE",null);
        } catch(RuntimeException failure) {
            recordSync("v:"+previousId,"UPDATE_RELATIONS","ERROR","No se pudo confirmar la relación de sucesión; reintente la publicación.");
            throw failure;
        }
    }

    /** Only the configured public origin is accepted; no arbitrary redirect target from a request. */
    public record LandingTargets(String versionDoi, String versionUrl, String conceptualDoi, String conceptualUrl) {}
    public LandingTargets landingTargets(String resourceId) {
        requireEnabled();
        ScientificRecord current=science(resourceId);
        if(current.getStatus()!=PublicationStatus.PUBLISHED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Solo versiones publicadas pueden actualizar su landing DOI.");
        String rootId=root(resourceId);
        DoiRegistration version=managedRegistration("v:"+resourceId);
        DoiRegistration concept=managedRegistration("c:"+rootId);
        if(!version.getDoi().equalsIgnoreCase(current.getVersionDoi()==null?"":current.getVersionDoi())
                || !concept.getDoi().equalsIgnoreCase(current.getConceptualDoi()==null?"":current.getConceptualDoi()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Los DOI locales requieren conciliación.");
        return new LandingTargets(version.getDoi(),landing(resourceId).toString(),concept.getDoi(),conceptLanding(rootId).toString());
    }
    private DoiRegistration managedRegistration(String key) {
        DoiRegistration registration=registrations.findById(key).orElseThrow(()->
            new ResponseStatusException(HttpStatus.CONFLICT,"DOI sin registro automático local; requiere conciliación."));
        if(!registration.getApiHost().equals(datacite.apiHost()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"El DOI pertenece a otro entorno DataCite.");
        return registration;
    }
    public LandingTargets refreshLandingUrls(String resourceId,LandingTargets confirmed,String actor) {
        LandingTargets target=landingTargets(resourceId);
        if(!target.equals(confirmed))throw new ResponseStatusException(HttpStatus.CONFLICT,"La configuración cambió. Revise y confirme de nuevo las URLs.");
        refreshLanding("v:"+resourceId,target.versionDoi(),target.versionUrl());
        refreshLanding("c:"+root(resourceId),target.conceptualDoi(),target.conceptualUrl());
        transactions.executeWithoutResult(status->editorialEvents.save(new ScientificRecordEvent(resourceId,actor,"DOI_URL_UPDATED",target.versionUrl())));
        return target;
    }
    private void refreshLanding(String key,String doi,String url) {
        try {
            var updated=datacite.updateUrl(doi,URI.create(url));
            if(!"findable".equalsIgnoreCase(updated.state()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"DataCite no confirmó el estado público del DOI.");
            recordSync(key,"UPDATE_URL","FINDABLE",null);
        } catch(RuntimeException failure) {
            recordSync(key,"UPDATE_URL","ERROR","No se pudo confirmar la URL; reintente la operación para conciliar.");
            throw failure;
        }
    }

    public DoiStatus status(String resourceId) {
        ScientificRecord science = science(resourceId);
        String rootId = root(resourceId);
        DoiRegistration concept = registrations.findById("c:" + rootId).orElse(null);
        DoiRegistration version = registrations.findById("v:" + resourceId).orElse(null);
        return new DoiStatus(resourceId, rootId, concept == null ? null : concept.getDoi(),
                concept == null ? "NOT_REQUESTED" : concept.getState(),
                version == null ? null : version.getDoi(),
                version == null ? "NOT_REQUESTED" : version.getState(), science.getStatus().name());
    }

    private void reconcileDraft(String key, String doi) {
        try {
            var remote = datacite.lookup(doi);
            if (remote.isEmpty()) {
                try { remote = java.util.Optional.of(datacite.reserveDraft(doi)); }
                catch (RuntimeException failure) {
                    // POST may have succeeded remotely while its response was lost.
                    remote = datacite.lookup(doi);
                    if (remote.isEmpty()) throw failure;
                }
            }
            String remoteState = remote.orElseThrow().state().toUpperCase(java.util.Locale.ROOT);
            if (!List.of("DRAFT", "REGISTERED", "FINDABLE").contains(remoteState))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Estado DOI no admitido por DataCite.");
            recordSync(key, "RESERVE", remoteState, null);
        } catch (RuntimeException failure) {
            recordSync(key, "RESERVE", "ERROR", "No se pudo confirmar la reserva; reintente para conciliar.");
            throw failure;
        }
    }

    private void publishRemote(String key, String doi, Map<String, Object> metadata) {
        boolean alreadyFindable = false;
        boolean transitionAttempted = false;
        try {
            var remote = datacite.lookup(doi).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No existe la reserva DOI en DataCite."));
            alreadyFindable = "findable".equalsIgnoreCase(remote.state());
            transitionAttempted = !alreadyFindable;
            var updated = alreadyFindable
                    ? datacite.updateMetadata(doi, metadata) : datacite.publish(doi, metadata);
            if (!"findable".equalsIgnoreCase(updated.state()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DataCite no confirmó DOI público.");
            recordSync(key, "PUBLISH", "FINDABLE", null);
        } catch (RuntimeException failure) {
            // The remote transition might have succeeded even if its HTTP response was lost.
            // For metadata updates of an already Findable DOI, state alone cannot prove success.
            if (transitionAttempted) {
                try {
                    var current = datacite.lookup(doi);
                    if (current.isPresent() && "findable".equalsIgnoreCase(current.get().state())) {
                        recordSync(key, "RECONCILE", "FINDABLE", null);
                        return;
                    }
                } catch (RuntimeException ignored) { /* preserve the original failure */ }
            }
            recordSync(key, "PUBLISH", "ERROR", "No se pudo confirmar la publicación; reintente para conciliar.");
            throw failure;
        }
    }

    private void recordSync(String key, String action, String result, String detail) {
        transactions.executeWithoutResult(status -> {
            DoiRegistration registration = registrations.findById(key).orElseThrow();
            registration.setState(result);
            registration.setLastSyncedAt(Instant.now());
            registration.setLastError(detail);
            registrations.save(registration);
            syncEvents.save(new DoiSyncEvent(key, action, result, detail));
        });
    }

    private void ensureRegistration(String key, String resourceId, String kind, String doi) {
        DoiRegistration existing = registrations.findById(key).orElse(null);
        if (existing == null) {
            registrations.saveAndFlush(new DoiRegistration(key, resourceId, kind, doi, datacite.apiHost()));
        } else if (!doi.equalsIgnoreCase(existing.getDoi()) || !datacite.apiHost().equals(existing.getApiHost())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La reserva DOI pertenece a otro prefijo o entorno DataCite; no se puede cambiar automáticamente.");
        }
    }

    private String root(String resourceId) {
        String current = resourceId;
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (true) {
            if (!visited.add(current)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ciclo en versiones del recurso.");
            ScientificRecord record = science(current);
            if (record.getPreviousResourceId() == null) return current;
            current = record.getPreviousResourceId();
        }
    }

    private ScientificRecord science(String id) {
        if (resources.findById(id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado.");
        return records.findById(id).orElseGet(() -> new ScientificRecord(id));
    }

    private String generatedDoi(String kind, String id) {
        String suffix = UUID.nameUUIDFromBytes((kind + ":" + id).getBytes(StandardCharsets.UTF_8)).toString();
        return datacite.prefix() + "/" + kind + "-" + suffix;
    }

    private URI landing(String id) { return publicUri("/datasets/" + id); }
    private URI conceptLanding(String id) { return publicUri("/datasets/" + id + "/concept"); }
    private URI publicUri(String path) {
        try {
            URI base = URI.create(publicBaseUrl);
            if ("https".equalsIgnoreCase(base.getScheme()) && base.getHost() != null
                    && base.getRawUserInfo() == null && base.getRawQuery() == null && base.getRawFragment() == null
                    && (base.getPath() == null || base.getPath().isBlank() || "/".equals(base.getPath())))
                return base.resolve(path);
        } catch (IllegalArgumentException ignored) { /* report invalid configuration consistently */ }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure repo.datacite.public-base-url con HTTPS público.");
    }

    private void requireEnabled() {
        if (!datacite.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integración DOI desactivada.");
    }

    private static Map<String, String> relation(String doi, String type) {
        return Map.of("relatedIdentifier", doi, "relatedIdentifierType", "DOI", "relationType", type);
    }

    public record DoiStatus(String resourceId, String rootResourceId, String conceptualDoi,
                            String conceptualState, String versionDoi, String versionState, String publicationStatus) {}
}
