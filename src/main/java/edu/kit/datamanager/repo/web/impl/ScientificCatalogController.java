package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ResourceType;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Real server-side paging and filtering for the authenticated catalogue. */
@RestController
@RequestMapping({"/api/v1/catalog", "/api/v1/public/catalog"})
public class ScientificCatalogController {
    private final IDataResourceDao resources;
    public ScientificCatalogController(IDataResourceDao resources) { this.resources = resources; }

    @GetMapping
    @Transactional(readOnly = true)
    public CatalogPage list(@RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "") String author,
                            @RequestParam(defaultValue = "") String type,
                            @RequestParam(defaultValue = "") String year,
                            @RequestParam(defaultValue = "") String license,
                            @RequestParam(defaultValue = "") String discipline,
                            @RequestParam(defaultValue = "false") boolean hasDoi,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            HttpServletRequest request) {
        final boolean publicOnly = request.getRequestURI().startsWith("/api/v1/public/");
        if (page < 0 || page > 100000 || size < 1 || size > 100 || q.length() > 200 || author.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetros de búsqueda no válidos.");
        }
        ResourceType.TYPE_GENERAL selectedType = null;
        if (!type.isBlank()) {
            try { selectedType = ResourceType.TYPE_GENERAL.valueOf(type); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de recurso no válido."); }
        }
        final ResourceType.TYPE_GENERAL category = selectedType;
        Specification<DataResource> spec = (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            if (!q.isBlank()) {
                String needle = "%" + q.trim().toLowerCase() + "%";
                var titles = root.join("titles", JoinType.LEFT);
                var creators = root.join("creators", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(titles.get("value")), needle),
                        cb.like(cb.lower(creators.get("givenName")), needle),
                        cb.like(cb.lower(creators.get("familyName")), needle),
                        cb.like(cb.lower(root.get("publisher")), needle),
                        cb.like(cb.lower(root.get("id")), needle),
                        cb.like(cb.lower(root.join("identifier", JoinType.LEFT).get("value")), needle)));
            }
            if (!author.isBlank()) {
                String needle = "%" + author.trim().toLowerCase() + "%";
                var creator = root.join("creators", JoinType.INNER);
                predicates.add(cb.or(cb.like(cb.lower(creator.get("givenName")), needle), cb.like(cb.lower(creator.get("familyName")), needle)));
            }
            if (category != null) predicates.add(cb.equal(root.join("resourceType", JoinType.INNER).get("typeGeneral"), category));
            if (!year.isBlank()) predicates.add(cb.equal(root.get("publicationYear"), year.trim()));
            if (!license.isBlank() || !discipline.isBlank() || hasDoi || publicOnly) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ScientificRecord> scientific = subquery.from(ScientificRecord.class);
                List<Predicate> terms = new ArrayList<>();
                terms.add(cb.equal(scientific.get("resourceId"), root.get("id")));
                if (!license.isBlank()) terms.add(cb.equal(cb.lower(scientific.get("licenseId")), license.trim().toLowerCase()));
                if (!discipline.isBlank()) terms.add(cb.like(cb.lower(scientific.get("discipline")), "%" + discipline.trim().toLowerCase() + "%"));
                if (hasDoi) terms.add(cb.isNotNull(scientific.get("versionDoi")));
                if (publicOnly) {
                    terms.add(cb.equal(scientific.get("status"), PublicationStatus.PUBLISHED));
                    terms.add(cb.equal(scientific.get("accessLevel"), "OPEN"));
                }
                subquery.select(scientific.get("resourceId")).where(terms.toArray(Predicate[]::new));
                predicates.add(cb.exists(subquery));
            }
            if (!publicOnly) {
                Subquery<String> anyRecord = query.subquery(String.class);
                Root<ScientificRecord> record = anyRecord.from(ScientificRecord.class);
                anyRecord.select(record.get("resourceId")).where(cb.equal(record.get("resourceId"), root.get("id")));
                Subquery<String> publishedRecord = query.subquery(String.class);
                Root<ScientificRecord> published = publishedRecord.from(ScientificRecord.class);
                publishedRecord.select(published.get("resourceId")).where(
                        cb.equal(published.get("resourceId"), root.get("id")),
                        cb.equal(published.get("status"), PublicationStatus.PUBLISHED));
                predicates.add(cb.or(cb.not(cb.exists(anyRecord)), cb.exists(publishedRecord)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<DataResource> result = resources.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastUpdate")));
        return new CatalogPage(result.getContent().stream().map(item -> new CatalogItem(item.getId(),
                item.getTitles().stream().findFirst().map(title -> title.getValue()).orElse("Sin título"),
                item.getCreators().stream().map(creator -> String.join(" ",
                        creator.getGivenName() == null ? "" : creator.getGivenName(),
                        creator.getFamilyName() == null ? "" : creator.getFamilyName()).trim()).toList(),
                item.getPublisher(), item.getPublicationYear(),
                item.getResourceType() == null ? null : item.getResourceType().getTypeGeneral().name(),
                item.getIdentifier() == null ? null : item.getIdentifier().getValue())).toList(),
                result.getTotalElements(), result.getNumber(), result.getTotalPages());
    }

    public record CatalogItem(String id, String title, List<String> authors, String publisher, String year, String type, String identifier) {}
    public record CatalogPage(List<CatalogItem> items, long total, int page, int pages) {}
}
