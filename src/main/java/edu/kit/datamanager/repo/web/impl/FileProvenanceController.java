package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.repository.FileProvenanceEventRepository;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** File-level provenance is private to the author and curatorial staff. */
@RestController
@RequestMapping("/api/v1/scientific/{id}/file-history")
public class FileProvenanceController {
    private final FileProvenanceEventRepository events;
    private final ResourceOwnershipRepository ownership;
    private final IDataResourceDao resources;

    public FileProvenanceController(FileProvenanceEventRepository events, ResourceOwnershipRepository ownership,
                                    IDataResourceDao resources) {
        this.events = events;
        this.ownership = ownership;
        this.resources = resources;
    }

    @GetMapping
    public Page<edu.kit.datamanager.repo.domain.FileProvenanceEvent> list(@PathVariable String id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginación no válida.");
        if (!resources.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean curator = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(item -> "ROLE_CURATOR".equals(item.getAuthority()) || "ROLE_ADMINISTRATOR".equals(item.getAuthority()));
        boolean owner = authentication != null && ownership.findById(id)
                .filter(item -> item.getUsername().equalsIgnoreCase(authentication.getName())).isPresent();
        if (!curator && !owner) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Historial reservado al autor y curación.");
        return events.findByResourceIdOrderByOccurredAtDescIdDesc(id, PageRequest.of(page, size));
    }
}
