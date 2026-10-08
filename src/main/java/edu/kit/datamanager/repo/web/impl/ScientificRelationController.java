package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRelation;
import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.ScientificRelationRepository;
import java.net.URI;
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

/** Controlled DataCite relationships; published versions are immutable. */
@RestController
@RequestMapping("/api/v1/scientific/{id}/relations")
public class ScientificRelationController {
    private final ScientificRelationRepository relations;
    private final ScientificRecordRepository records;
    private final ResourceOwnershipRepository ownership;
    private final ScientificRecordEventRepository events;

    public ScientificRelationController(ScientificRelationRepository relations, ScientificRecordRepository records,
            ResourceOwnershipRepository ownership, ScientificRecordEventRepository events) {
        this.relations = relations; this.records = records; this.ownership = ownership; this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ScientificRelation> list(@PathVariable String id) {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getStatus() != PublicationStatus.PUBLISHED && !isOwnerOrCurator(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return relations.findByResourceIdOrderByIdAsc(id);
    }

    @PutMapping
    @Transactional
    public List<ScientificRelation> replace(@PathVariable String id, @RequestBody List<RelationInput> input) {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isOwner(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede editar relaciones.");
        if (record.getStatus() != PublicationStatus.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden editar relaciones de un borrador.");
        if (input == null || input.size() > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se admiten hasta 30 relaciones.");
        var unique = new HashSet<String>();
        var values = input.stream().map(item -> {
            if (item == null || item.kind() == null || item.identifierType() == null || item.relationType() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete el tipo de cada relación.");
            String identifier = clean(item.identifier(), 500);
            if (identifier == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el identificador de una relación.");
            if (item.identifierType() == ScientificRelation.IdentifierType.DOI) {
                identifier = identifier.replaceFirst("(?i)^https?://(?:dx\\.)?doi\\.org/", "");
                if (!identifier.matches("(?i)^10\\.\\d{4,9}/\\S+$"))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOI relacionado no válido.");
            } else {
                try {
                    URI uri = URI.create(identifier);
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                        throw new IllegalArgumentException();
                } catch (IllegalArgumentException error) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La URL relacionada debe ser HTTPS válida.");
                }
            }
            String key = item.identifierType() + ":" + identifier.toLowerCase() + ":" + item.relationType();
            if (!unique.add(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Relación duplicada.");
            return new ScientificRelation(id, item.kind(), item.identifierType(), item.relationType(),
                    identifier, clean(item.title(), 255));
        }).toList();
        relations.deleteByResourceId(id);
        var saved = relations.saveAll(values);
        events.save(new ScientificRecordEvent(id, username(), "RELATIONS_UPDATED", Integer.toString(saved.size())));
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
        if (clean.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un campo supera el límite permitido.");
        return clean;
    }

    public record RelationInput(ScientificRelation.Kind kind, ScientificRelation.IdentifierType identifierType,
                                ScientificRelation.RelationType relationType, String identifier, String title) {}
}
