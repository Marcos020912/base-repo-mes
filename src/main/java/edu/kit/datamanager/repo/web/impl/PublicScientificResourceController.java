package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.FileFixityStateRepository;
import edu.kit.datamanager.repo.repository.ScientificRelationRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificFundingRepository;
import edu.kit.datamanager.repo.repository.ScientificAffiliationRepository;
import edu.kit.datamanager.repo.domain.ScientificFunding;
import edu.kit.datamanager.repo.domain.ScientificAffiliation;
import edu.kit.datamanager.repo.domain.ScientificRelation;
import edu.kit.datamanager.repo.domain.FileFixityState;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public landing pages expose released metadata; file access follows the publication access policy. */
@RestController
@RequestMapping("/api/v1/public/resources")
public class PublicScientificResourceController {
    private final IDataResourceDao resources;
    private final ScientificRecordRepository records;
    private final IContentInformationDao contents;
    private final FileFixityStateRepository fixityStates;
    private final ScientificRelationRepository relations;
    private final ScientificCreatorRepository creators;
    private final ScientificFundingRepository funding;
    private final ScientificAffiliationRepository affiliations;

    public PublicScientificResourceController(IDataResourceDao resources, ScientificRecordRepository records, IContentInformationDao contents,
                                              FileFixityStateRepository fixityStates, ScientificRelationRepository relations,
                                              ScientificCreatorRepository creators, ScientificFundingRepository funding,
                                              ScientificAffiliationRepository affiliations) {
        this.resources = resources;
        this.records = records;
        this.contents = contents;
        this.fixityStates = fixityStates;
        this.relations = relations;
        this.creators = creators;
        this.funding = funding;
        this.affiliations = affiliations;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> detail(@PathVariable String id) throws IOException {
        ScientificRecord science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        DataResource resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String title = resource.getTitles().stream().findFirst().map(item -> item.getValue()).orElse("Sin título");
        if (science.getStatus() == PublicationStatus.WITHDRAWN) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("title", title, "doi", science.getVersionDoi() == null ? "" : science.getVersionDoi(),
                    "reason", science.getWithdrawalReason() == null ? "Retirado" : science.getWithdrawalReason()));
        }
        requirePublished(science);
        List<String> authors = resource.getCreators().stream().map(item -> String.join(" ",
                item.getGivenName() == null ? "" : item.getGivenName(),
                item.getFamilyName() == null ? "" : item.getFamilyName()).trim()).toList();
        var details = creators.findByResourceId(id).stream().collect(java.util.stream.Collectors.toMap(
                edu.kit.datamanager.repo.domain.ScientificCreator::getCreatorId, item -> item));
        var organizations = affiliations.findByResourceIdOrderByCreatorIdAscSortOrderAsc(id).stream().collect(
                java.util.stream.Collectors.groupingBy(ScientificAffiliation::getCreatorId,
                        java.util.stream.Collectors.mapping(item -> new AuthorAffiliation(item.getInstitution(), item.getRor()),
                                java.util.stream.Collectors.toList())));
        List<AuthorIdentity> authorIdentities = resource.getCreators().stream().map(item -> {
            var identity = details.get(item.getId());
            List<AuthorAffiliation> knownAffiliations = organizations.getOrDefault(item.getId(), List.of());
            if (knownAffiliations.isEmpty() && identity != null && identity.getInstitution() != null)
                knownAffiliations = List.of(new AuthorAffiliation(identity.getInstitution(), identity.getRor()));
            return new AuthorIdentity(item.getGivenName(), item.getFamilyName(),
                    identity == null ? null : identity.getOrcid(),
                    identity == null ? null : identity.getInstitution(),
                    identity == null ? null : identity.getRor(), knownAffiliations);
        }).toList();
        String markdown = contents.findByParentResourceAndRelativePath(resource, "description.md")
                .map(info -> {
                    try {
                        Path path = localPath(info);
                        return Files.size(path) <= 1024 * 1024 ? Files.readString(path) : "Descripción demasiado grande para mostrarla aquí.";
                    } catch (IOException | IllegalArgumentException ex) { return "La descripción no está disponible."; }
                }).orElse("No hay descripción.");
        String newerVersionId = records.findFirstByPreviousResourceIdAndStatusOrderByPublishedAtDesc(id, PublicationStatus.PUBLISHED)
                .map(ScientificRecord::getResourceId).orElse(null);
        return ResponseEntity.ok(new PublicDetail(id, title, authors, resource.getPublisher(), resource.getPublicationYear(),
                resource.getResourceType() == null ? null : resource.getResourceType().getTypeGeneral().name(),
                science.getVersionLabel(), science.getVersionDoi(), science.getConceptualDoi(), science.getLicenseId(),
                science.getInstitution(), science.getOrcid(), science.getRor(), science.getLanguage(), science.getDiscipline(),
                science.getKeywords(), science.getRelatedPublications(), science.getMethodology(), science.getPublishedAt(), markdown,
                science.getPreviousResourceId(), newerVersionId, science.getAccessLevel(), science.getEmbargoUntil(),
                relations.findByResourceIdOrderByIdAsc(id), authorIdentities,
                funding.findByResourceIdOrderByIdAsc(id)));
    }

    @GetMapping("/{id}/files")
    @Transactional(readOnly = true)
    public PublicFiles files(@PathVariable String id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginación no válida.");
        DataResource resource = publishedResource(id);
        Page<ContentInformation> result = contents.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("parentResource"), resource), cb.notEqual(root.get("relativePath"), "description.md")), PageRequest.of(page, size));
        java.util.Map<Long, FileFixityState> fixityById = new java.util.HashMap<>();
        fixityStates.findAllById(result.getContent().stream().map(ContentInformation::getId).toList())
                .forEach(state -> fixityById.put(state.getContentId(), state));
        return new PublicFiles(result.getContent().stream()
                .map(info -> new FileItem(info.getRelativePath(), info.getSize(), info.getMediaType(),
                        info.getMetadata() == null ? null : info.getMetadata().get("sha256"),
                        fixityById.containsKey(info.getId()) ? fixityById.get(info.getId()).getStatus() : null,
                        fixityById.containsKey(info.getId()) ? fixityById.get(info.getId()).getCheckedAt() : null)).toList(), result.getTotalElements(), result.getNumber(), result.getTotalPages());
    }

    @GetMapping("/{id}/file")
    public void file(@PathVariable String id, @RequestParam String path, @RequestParam(defaultValue = "false") boolean inline,
                     HttpServletResponse response) throws IOException {
        DataResource resource = publishedResource(id);
        ScientificRecord science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean open = "OPEN".equals(science.getAccessLevel()) ||
                ("EMBARGOED".equals(science.getAccessLevel()) && science.getEmbargoUntil() != null && !science.getEmbargoUntil().isAfter(java.time.Instant.now()));
        if (!open) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Los archivos no son de acceso abierto.");
        ContentInformation info = contents.findByParentResourceAndRelativePath(resource, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Path file = localPath(info);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String mediaType = info.getMediaType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : info.getMediaType();
        boolean safeImage = inline && List.of("image/png", "image/jpeg", "image/gif", "image/webp").contains(mediaType);
        response.setContentType(safeImage ? mediaType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", (safeImage ? "inline" : "attachment") + "; filename=\"" + file.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_") + "\"");
        response.setContentLengthLong(Files.size(file));
        try (var input = Files.newInputStream(file)) { input.transferTo(response.getOutputStream()); }
    }

    private DataResource publishedResource(String id) {
        ScientificRecord science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requirePublished(science);
        return resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void requirePublished(ScientificRecord science) {
        if (science.getStatus() != PublicationStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static Path localPath(ContentInformation info) {
        if (info.getContentUri() == null || !info.getContentUri().startsWith("file:")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return Path.of(URI.create(info.getContentUri()));
    }

    public record PublicDetail(String id, String title, List<String> authors, String publisher, String year, String type,
                               String version, String doi, String conceptualDoi, String license, String institution,
                               String orcid, String ror, String language, String discipline, String keywords,
                               String relatedPublications, String methodology, java.time.Instant publishedAt, String markdown,
                               String previousResourceId, String newerVersionId, String accessLevel, java.time.Instant embargoUntil,
                               List<ScientificRelation> relations, List<AuthorIdentity> authorIdentities,
                               List<ScientificFunding> funding) {}
    public record AuthorIdentity(String givenName, String familyName, String orcid, String institution,
                                 String ror, List<AuthorAffiliation> affiliations) {}
    public record AuthorAffiliation(String institution, String ror) {}
    public record FileItem(String path, long size, String mediaType, String sha256, String fixityStatus, java.time.Instant fixityCheckedAt) {}
    public record PublicFiles(List<FileItem> files, long total, int page, int pages) {}
}
