package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

/** ORCID and institutional affiliation are bound to an exact creator ID. */
@RestController
@RequestMapping("/api/v1/scientific/{id}/creators")
public class ScientificCreatorController {
    private final IDataResourceDao resources;
    private final ScientificRecordRepository records;
    private final ScientificCreatorRepository creators;
    private final ResourceOwnershipRepository ownership;
    private final ScientificRecordEventRepository events;

    public ScientificCreatorController(IDataResourceDao resources, ScientificRecordRepository records,
            ScientificCreatorRepository creators, ResourceOwnershipRepository ownership,
            ScientificRecordEventRepository events) {
        this.resources = resources; this.records = records; this.creators = creators;
        this.ownership = ownership; this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CreatorView> list(@PathVariable String id) {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (record.getStatus() != PublicationStatus.PUBLISHED && !isOwnerOrCurator(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<Long, ScientificCreator> details = creators.findByResourceId(id).stream()
                .collect(Collectors.toMap(ScientificCreator::getCreatorId, Function.identity()));
        return resource.getCreators().stream().map(agent -> {
            ScientificCreator item = details.get(agent.getId());
            return new CreatorView(agent.getId(), agent.getGivenName(), agent.getFamilyName(),
                    item == null ? null : item.getOrcid(), item == null ? null : item.getInstitution(),
                    item == null ? null : item.getRor());
        }).toList();
    }

    @PutMapping
    @Transactional
    public List<CreatorView> replace(@PathVariable String id, @RequestBody List<CreatorInput> input) {
        var record = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isOwner(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede editar identidades.");
        if (record.getStatus() != PublicationStatus.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede editar un borrador.");
        var resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<Long, Agent> known = resource.getCreators().stream()
                .filter(agent -> agent.getId() != null).collect(Collectors.toMap(Agent::getId, Function.identity()));
        if (input == null || input.size() > 100 || input.size() != known.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique exactamente todos los autores actuales.");
        var seen = new HashSet<Long>();
        var values = input.stream().map(item -> {
            if (item == null || item.creatorId() == null || !known.containsKey(item.creatorId()) || !seen.add(item.creatorId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Autor inexistente o repetido.");
            String orcid = clean(item.orcid());
            if (orcid != null && !orcid.matches("(?:https://orcid.org/)?\\d{4}-\\d{4}-\\d{4}-[\\dX]{4}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ORCID no válido.");
            String institution = clean(item.institution());
            String ror = clean(item.ror());
            if (ror != null && (institution == null || !ror.matches("(?:https://ror.org/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROR no válido o sin institución.");
            return new ScientificCreator(id, item.creatorId(), orcid, institution, ror);
        }).toList();
        creators.deleteByResourceId(id);
        creators.saveAll(values);
        events.save(new ScientificRecordEvent(id, username(), "CREATORS_UPDATED", Integer.toString(values.size())));
        return list(id);
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

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor demasiado largo.");
        return clean;
    }

    public record CreatorInput(Long creatorId, String orcid, String institution, String ror) {}
    public record CreatorView(Long creatorId, String givenName, String familyName, String orcid, String institution, String ror) {}
}
