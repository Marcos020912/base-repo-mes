package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.FileFixityState;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.FileFixityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Curators may request an integrity check without changing the recorded upload digest. */
@RestController
@RequestMapping("/api/v1/scientific/{id}/fixity")
@PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
public class ScientificFixityController {
    private final IDataResourceDao resources;
    private final IContentInformationDao contents;
    private final FileFixityService fixity;
    private final ScientificRecordRepository records;

    public ScientificFixityController(IDataResourceDao resources, IContentInformationDao contents,
                                    FileFixityService fixity, ScientificRecordRepository records) {
        this.resources = resources; this.contents = contents; this.fixity = fixity; this.records = records;
    }

    @PostMapping
    public FileFixityState verify(@PathVariable String id, @RequestParam String path) {
        if (path == null || path.isBlank() || path.length() > 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta no válida.");
        if (!records.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var content = contents.findByParentResourceAndRelativePath(resource, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return fixity.verify(content);
    }
}
