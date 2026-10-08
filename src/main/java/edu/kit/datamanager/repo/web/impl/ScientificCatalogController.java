package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ResourceType;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
    private final ScientificRecordRepository scientificRecords;
    private final EntityManager entityManager;
    public ScientificCatalogController(IDataResourceDao resources, ScientificRecordRepository scientificRecords,
                                       EntityManager entityManager) {
        this.resources = resources; this.scientificRecords = scientificRecords; this.entityManager = entityManager;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public CatalogPage list(@RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "") String author,
                            @RequestParam(defaultValue = "") String type,
                            @RequestParam(defaultValue = "") String year,
                            @RequestParam(defaultValue = "") String license,
                            @RequestParam(defaultValue = "") String discipline,
                            @RequestParam(defaultValue = "") String institution,
                            @RequestParam(defaultValue = "") String language,
                            @RequestParam(defaultValue = "") String access,
                            @RequestParam(defaultValue = "") String format,
                            @RequestParam(defaultValue = "false") boolean hasDoi,
                            @RequestParam(defaultValue = "false") boolean withoutDoi,
                            @RequestParam(defaultValue = "newest") String sort,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            HttpServletRequest request) {
        final boolean publicOnly = request.getRequestURI().startsWith("/api/v1/public/");
        if (page < 0 || page > 100000 || size < 1 || size > 100 || q.length() > 200 || author.length() > 200 ||
                institution.length() > 200 || language.length() > 16 || !format.matches("[a-zA-Z0-9]{0,16}") ||
                (!access.isBlank() && !List.of("OPEN", "RESTRICTED", "EMBARGOED").contains(access)) ||
                !List.of("newest", "oldest", "year_asc", "year_desc").contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetros de búsqueda no válidos.");
        }
        if (!type.isBlank()) {
            try { ResourceType.TYPE_GENERAL.valueOf(type); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de recurso no válido."); }
        }
        if (hasDoi && withoutDoi) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtros de DOI incompatibles.");
        Specification<DataResource> spec = specification(q, author, type, year, license, discipline, institution, language, access, format, hasDoi, withoutDoi, publicOnly);
        Sort order = switch (sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "lastUpdate", "id");
            case "year_asc" -> Sort.by(Sort.Direction.ASC, "publicationYear", "id");
            case "year_desc" -> Sort.by(Sort.Direction.DESC, "publicationYear", "id");
            default -> Sort.by(Sort.Direction.DESC, "lastUpdate", "id");
        };
        Page<DataResource> result = resources.findAll(spec, PageRequest.of(page, size, order));
        java.util.Map<String, ScientificRecord> scienceById = new java.util.HashMap<>();
        scientificRecords.findAllById(result.getContent().stream().map(DataResource::getId).toList())
                .forEach(record -> scienceById.put(record.getResourceId(), record));
        return new CatalogPage(result.getContent().stream().map(item -> new CatalogItem(item.getId(),
                item.getTitles().stream().findFirst().map(title -> title.getValue()).orElse("Sin título"),
                item.getCreators().stream().map(creator -> String.join(" ",
                        creator.getGivenName() == null ? "" : creator.getGivenName(),
                        creator.getFamilyName() == null ? "" : creator.getFamilyName()).trim()).toList(),
                item.getPublisher(), item.getPublicationYear(),
                item.getResourceType() == null ? null : item.getResourceType().getTypeGeneral().name(),
                scienceById.containsKey(item.getId()) ? scienceById.get(item.getId()).getVersionDoi() :
                        item.getIdentifier() == null ? null : item.getIdentifier().getValue(),
                scienceById.containsKey(item.getId()) ? scienceById.get(item.getId()).getAccessLevel() : null)).toList(),
                result.getTotalElements(), result.getNumber(), result.getTotalPages());
    }

    private static Specification<DataResource> specification(String q, String author, String type, String year,
            String license, String discipline, String institution, String language, String access,
            String format, boolean hasDoi, boolean withoutDoi, boolean publicOnly) {
        final ResourceType.TYPE_GENERAL category;
        if (type.isBlank()) category = null;
        else category = ResourceType.TYPE_GENERAL.valueOf(type);
        return (root, query, cb) -> {
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
                Expression<String> fullName = cb.concat(cb.concat(cb.coalesce(creator.get("givenName"), ""), " "),
                        cb.coalesce(creator.get("familyName"), ""));
                predicates.add(cb.or(cb.like(cb.lower(creator.get("givenName")), needle),
                        cb.like(cb.lower(creator.get("familyName")), needle),
                        cb.like(cb.lower(fullName), needle)));
            }
            if (category != null) predicates.add(cb.equal(root.join("resourceType", JoinType.INNER).get("typeGeneral"), category));
            if (!year.isBlank()) predicates.add(cb.equal(root.get("publicationYear"), year.trim()));
            if (!license.isBlank() || !discipline.isBlank() || !institution.isBlank() || !language.isBlank() || !access.isBlank() || hasDoi || withoutDoi || publicOnly) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ScientificRecord> scientific = subquery.from(ScientificRecord.class);
                List<Predicate> terms = new ArrayList<>();
                terms.add(cb.equal(scientific.get("resourceId"), root.get("id")));
                if (!license.isBlank()) terms.add(cb.equal(cb.lower(scientific.get("licenseId")), license.trim().toLowerCase()));
                if (!discipline.isBlank()) terms.add(cb.like(cb.lower(scientific.get("discipline")), "%" + discipline.trim().toLowerCase() + "%"));
                if (!institution.isBlank()) terms.add(cb.like(cb.lower(scientific.get("institution")), "%" + institution.trim().toLowerCase() + "%"));
                if (!language.isBlank()) terms.add(cb.equal(cb.lower(scientific.get("language")), language.trim().toLowerCase()));
                if (!access.isBlank()) terms.add(cb.equal(scientific.get("accessLevel"), access));
                if (hasDoi) terms.add(cb.isNotNull(scientific.get("versionDoi")));
                if (withoutDoi) terms.add(cb.isNull(scientific.get("versionDoi")));
                if (publicOnly) {
                    terms.add(cb.equal(scientific.get("status"), PublicationStatus.PUBLISHED));
                }
                subquery.select(scientific.get("resourceId")).where(terms.toArray(Predicate[]::new));
                predicates.add(cb.exists(subquery));
            }
            if (!format.isBlank()) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<ContentInformation> content = subquery.from(ContentInformation.class);
                subquery.select(content.get("id")).where(
                        cb.equal(content.get("parentResource"), root),
                        cb.like(cb.lower(content.get("relativePath")), "%." + format.toLowerCase()));
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
    }

    public record CatalogItem(String id, String title, List<String> authors, String publisher, String year, String type, String identifier, String accessLevel) {}
    public record CatalogPage(List<CatalogItem> items, long total, int page, int pages) {}

    @GetMapping("/facets")
    @Transactional(readOnly = true)
    public Map<String, List<FacetOption>> facets(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String author, @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String year, @RequestParam(defaultValue = "") String license,
            @RequestParam(defaultValue = "") String discipline, @RequestParam(defaultValue = "") String institution,
            @RequestParam(defaultValue = "") String language, @RequestParam(defaultValue = "") String access,
            @RequestParam(defaultValue = "") String format, @RequestParam(defaultValue = "false") boolean hasDoi,
            @RequestParam(defaultValue = "false") boolean withoutDoi,
            HttpServletRequest request) {
        if (q.length() > 200 || author.length() > 200 || year.length() > 4 || license.length() > 100 ||
                discipline.length() > 255 || institution.length() > 200 || language.length() > 16 ||
                !format.matches("[a-zA-Z0-9]{0,16}") ||
                (!access.isBlank() && !List.of("OPEN", "RESTRICTED", "EMBARGOED").contains(access)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetros de facetas no válidos.");
        if (!type.isBlank()) {
            try { ResourceType.TYPE_GENERAL.valueOf(type); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de recurso no válido."); }
        }
        if (hasDoi && withoutDoi) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtros de DOI incompatibles.");
        CatalogFilters filters = new CatalogFilters(q, author, type, year, license, discipline, institution,
                language, access, format, hasDoi, withoutDoi);
        boolean publicOnly = request.getRequestURI().startsWith("/api/v1/public/");
        Map<String, List<FacetOption>> result = new LinkedHashMap<>();
        for (String dimension : List.of("type", "author", "year", "access", "license", "discipline", "institution", "language", "hasDoi"))
            result.put(dimension, facetValues(dimension, filters.without(dimension), publicOnly));
        return result;
    }

    private List<FacetOption> facetValues(String dimension, CatalogFilters filter, boolean publicOnly) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<DataResource> root = query.from(DataResource.class);
        Root<ScientificRecord> science = query.from(ScientificRecord.class);
        Expression<?> value = switch (dimension) {
            case "type" -> root.join("resourceType", JoinType.LEFT).get("typeGeneral");
            case "author" -> {
                var creator = root.join("creators", JoinType.INNER);
                yield cb.concat(cb.concat(cb.coalesce(creator.get("givenName"), ""), " "),
                        cb.coalesce(creator.get("familyName"), ""));
            }
            case "year" -> root.get("publicationYear");
            case "access" -> science.get("accessLevel");
            case "license" -> science.get("licenseId");
            case "discipline" -> science.get("discipline");
            case "institution" -> science.get("institution");
            case "language" -> science.get("language");
            case "hasDoi" -> cb.<String>selectCase().when(cb.isNotNull(science.get("versionDoi")), "true").otherwise("false");
            default -> throw new IllegalArgumentException("Faceta no admitida.");
        };
        var condition = specification(filter.q, filter.author, filter.type, filter.year, filter.license,
                filter.discipline, filter.institution, filter.language, filter.access, filter.format,
                filter.hasDoi, filter.withoutDoi, publicOnly).toPredicate(root, query, cb);
        Expression<Long> count = cb.countDistinct(root.get("id"));
        query.multiselect(value.alias("value"), count.alias("count"))
                .where(cb.and(condition, cb.equal(science.get("resourceId"), root.get("id")),
                        cb.equal(science.get("status"), PublicationStatus.PUBLISHED)))
                .groupBy(value).orderBy(cb.desc(count));
        return entityManager.createQuery(query).setMaxResults(30).getResultList().stream()
                .filter(row -> row.get("value") != null && !row.get("value").toString().isBlank())
                .map(row -> new FacetOption(row.get("value").toString(), ((Number) row.get("count")).longValue()))
                .toList();
    }

    private record CatalogFilters(String q, String author, String type, String year, String license,
            String discipline, String institution, String language, String access, String format, boolean hasDoi, boolean withoutDoi) {
        CatalogFilters without(String dimension) {
            return new CatalogFilters(q, dimension.equals("author") ? "" : author, dimension.equals("type") ? "" : type,
                    dimension.equals("year") ? "" : year, dimension.equals("license") ? "" : license,
                    dimension.equals("discipline") ? "" : discipline,
                    dimension.equals("institution") ? "" : institution,
                    dimension.equals("language") ? "" : language,
                    dimension.equals("access") ? "" : access, format,
                    !dimension.equals("hasDoi") && hasDoi,
                    !dimension.equals("hasDoi") && withoutDoi);
        }
    }

    public record FacetOption(String value, long count) {}
}
