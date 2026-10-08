package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificAffiliation;
import edu.kit.datamanager.repo.domain.ScientificRecordEvent;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificAffiliationRepository;
import java.util.ArrayList;
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
    private final ScientificAffiliationRepository affiliations;
    private final ResourceOwnershipRepository ownership;
    private final ScientificRecordEventRepository events;

    public ScientificCreatorController(IDataResourceDao resources, ScientificRecordRepository records,
            ScientificCreatorRepository creators, ScientificAffiliationRepository affiliations,
            ResourceOwnershipRepository ownership,
            ScientificRecordEventRepository events) {
        this.resources = resources; this.records = records; this.creators = creators;
        this.affiliations = affiliations; this.ownership = ownership; this.events = events;
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
        Map<Long, List<AffiliationView>> organizations = affiliations.findByResourceIdOrderByCreatorIdAscSortOrderAsc(id)
                .stream().collect(Collectors.groupingBy(ScientificAffiliation::getCreatorId,
                        Collectors.mapping(item -> new AffiliationView(item.getInstitution(), item.getRor()), Collectors.toList())));
        return resource.getCreators().stream().map(agent -> {
            ScientificCreator item = details.get(agent.getId());
            List<AffiliationView> knownAffiliations = organizations.getOrDefault(agent.getId(), List.of());
            if (knownAffiliations.isEmpty() && item != null && item.getInstitution() != null)
                knownAffiliations = List.of(new AffiliationView(item.getInstitution(), item.getRor()));
            return new CreatorView(agent.getId(), agent.getGivenName(), agent.getFamilyName(),
                    item == null ? null : item.getOrcid(), item == null ? null : item.getInstitution(),
                    item == null ? null : item.getRor(), knownAffiliations);
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
        var newAffiliations = new ArrayList<ScientificAffiliation>();
        var values = input.stream().map(item -> {
            if (item == null || item.creatorId() == null || !known.containsKey(item.creatorId()) || !seen.add(item.creatorId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Autor inexistente o repetido.");
            String orcid = clean(item.orcid());
            if (orcid != null && !orcid.matches("(?:https://orcid.org/)?\\d{4}-\\d{4}-\\d{4}-[\\dX]{4}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ORCID no válido.");
            List<AffiliationInput> supplied = item.affiliations();
            if (supplied == null) {
                String legacyName = clean(item.institution());
                String legacyRor = clean(item.ror());
                supplied = legacyName == null && legacyRor == null ? List.of() : List.of(new AffiliationInput(legacyName, legacyRor));
            }
            if (supplied.size() > 10)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se admiten hasta diez instituciones por autor.");
            var unique = new HashSet<String>();
            for (int position = 0; position < supplied.size(); position++) {
                AffiliationInput affiliation = supplied.get(position);
                if (affiliation == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institución no válida.");
                String institution = clean(affiliation.institution());
                String ror = clean(affiliation.ror());
                if (institution == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el nombre de cada institución.");
                if (ror != null && !ror.matches("(?:https://ror.org/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}"))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROR institucional no válido.");
                String key = institution.toLowerCase(java.util.Locale.ROOT) + ":" + (ror == null ? "" : ror.toLowerCase(java.util.Locale.ROOT));
                if (!unique.add(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Institución duplicada para un autor.");
                newAffiliations.add(new ScientificAffiliation(id, item.creatorId(), position, institution, ror));
            }
            ScientificAffiliation first = newAffiliations.stream().filter(value -> value.getCreatorId().equals(item.creatorId()))
                    .findFirst().orElse(null);
            return new ScientificCreator(id, item.creatorId(), orcid,
                    first == null ? null : first.getInstitution(), first == null ? null : first.getRor());
        }).toList();
        affiliations.deleteByResourceId(id);
        creators.deleteByResourceId(id);
        creators.saveAll(values);
        affiliations.saveAll(newAffiliations);
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

    public record AffiliationInput(String institution, String ror) {}
    public record AffiliationView(String institution, String ror) {}
    public record CreatorInput(Long creatorId, String orcid, String institution, String ror,
                               List<AffiliationInput> affiliations) {
        public CreatorInput(Long creatorId, String orcid, String institution, String ror) {
            this(creatorId, orcid, institution, ror, null);
        }
    }
    public record CreatorView(Long creatorId, String givenName, String familyName, String orcid,
                              String institution, String ror, List<AffiliationView> affiliations) {}
}
