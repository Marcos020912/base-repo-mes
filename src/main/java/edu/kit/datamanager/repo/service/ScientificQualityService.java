package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Deterministic deposit checklist; it does not replace human curation. */
@Service
public class ScientificQualityService {
    private final IDataResourceDao resources;
    private final IContentInformationDao contents;
    private final ScientificCreatorRepository creators;

    public ScientificQualityService(IDataResourceDao resources, IContentInformationDao contents, ScientificCreatorRepository creators) {
        this.resources = resources;
        this.contents = contents;
        this.creators = creators;
    }

    public QualityReport inspect(ScientificRecord science) {
        DataResource resource = resources.findById(science.getResourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado."));
        var identities = creators.findByResourceId(science.getResourceId());
        List<QualityCheck> checks = new ArrayList<>();
        checks.add(new QualityCheck("title", "Título", resource.getTitles() != null && resource.getTitles().stream()
                .anyMatch(item -> hasText(item.getValue())), true));
        checks.add(new QualityCheck("authors", "Autoría", resource.getCreators() != null && !resource.getCreators().isEmpty(), true));
        checks.add(new QualityCheck("version", "Versión", hasText(science.getVersionLabel()), true));
        checks.add(new QualityCheck("license", "Licencia", hasText(science.getLicenseId()), true));
        checks.add(new QualityCheck("institution", "Institución", hasText(science.getInstitution()) || identities.stream().anyMatch(item -> hasText(item.getInstitution())), true));
        checks.add(new QualityCheck("methodology", "Metodología", hasText(science.getMethodology()), true));
        checks.add(new QualityCheck("description", "description.md", contents.findByParentResourceAndRelativePath(resource, "description.md").isPresent(), true));
        boolean hasDataFile = contents.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("parentResource"), resource),
                cb.notEqual(root.get("relativePath"), "description.md"),
                cb.notLike(root.get("relativePath"), "description/%")), PageRequest.of(0, 1)).hasContent();
        checks.add(new QualityCheck("files", "Al menos un archivo de datos", hasDataFile, true));
        checks.add(new QualityCheck("doi", "DOI registrado para esta versión", hasText(science.getVersionDoi()), false));
        checks.add(new QualityCheck("orcid", "ORCID de autoría", hasText(science.getOrcid()) || identities.stream().anyMatch(item -> hasText(item.getOrcid())), false));
        checks.add(new QualityCheck("ror", "ROR institucional", hasText(science.getRor()) || identities.stream().anyMatch(item -> hasText(item.getRor())), false));
        long completed = checks.stream().filter(QualityCheck::complete).count();
        return new QualityReport((int) (100 * completed / checks.size()),
                checks.stream().filter(item -> item.required() && !item.complete()).map(QualityCheck::label).toList(), checks);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    public record QualityCheck(String code, String label, boolean complete, boolean required) {}
    public record QualityReport(int completionPercent, List<String> blockers, List<QualityCheck> checks) {}
}
