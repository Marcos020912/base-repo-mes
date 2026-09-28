package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.domain.DoiSyncEvent;
import edu.kit.datamanager.repo.repository.DoiSyncEventRepository;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.service.DoiWorkflowService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/scientific")
public class DoiWorkflowController {
    private final DoiWorkflowService workflow;
    private final ResourceOwnershipRepository ownership;
    private final DoiSyncEventRepository events;

    public DoiWorkflowController(DoiWorkflowService workflow, ResourceOwnershipRepository ownership, DoiSyncEventRepository events) {
        this.workflow = workflow;
        this.ownership = ownership;
        this.events = events;
    }

    @GetMapping("/doi/config")
    public Map<String, Boolean> config() { return Map.of("enabled", workflow.enabled()); }

    @GetMapping("/{id}/doi")
    public DoiWorkflowService.DoiStatus status(@PathVariable String id) {
        requireOwnerOrCurator(id);
        return workflow.status(id);
    }

    @GetMapping("/{id}/doi/history")
    public List<DoiSyncEvent> history(@PathVariable String id) {
        requireOwnerOrCurator(id);
        var state = workflow.status(id);
        List<DoiSyncEvent> result = new java.util.ArrayList<>(events.findByRegistrationKeyOrderByOccurredAtAscIdAsc("c:" + state.rootResourceId()));
        result.addAll(events.findByRegistrationKeyOrderByOccurredAtAscIdAsc("v:" + id));
        result.sort(Comparator.comparing(DoiSyncEvent::getOccurredAt).thenComparing(DoiSyncEvent::getId));
        return result;
    }

    @PostMapping("/{id}/doi/reserve")
    public DoiWorkflowService.DoiStatus reserve(@PathVariable String id) {
        requireOwner(id);
        return workflow.reserve(id);
    }

    @PostMapping("/{id}/doi/publish")
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public edu.kit.datamanager.repo.domain.ScientificRecord publish(@PathVariable String id) {
        return workflow.publish(id, username());
    }

    private void requireOwnerOrCurator(String id) {
        if (!curator()) requireOwner(id);
    }

    private void requireOwner(String id) {
        if (ownership.findById(id).filter(item -> item.getUsername().equalsIgnoreCase(username())).isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede reservar el DOI de este depósito.");
    }

    private static boolean curator() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(item -> "ROLE_CURATOR".equals(item.getAuthority()) || "ROLE_ADMINISTRATOR".equals(item.getAuthority()));
    }

    private static String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}
