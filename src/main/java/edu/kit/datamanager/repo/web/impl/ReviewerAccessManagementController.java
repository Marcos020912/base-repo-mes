package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.service.ReviewerAccessService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Only curators can issue and revoke external review capabilities. */
@RestController
@RequestMapping("/api/v1/scientific/{id}/review-links")
@PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
public class ReviewerAccessManagementController {
    private final ReviewerAccessService service;
    public ReviewerAccessManagementController(ReviewerAccessService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewerAccessService.CreatedLink create(@PathVariable String id, @RequestBody DurationRequest input) {
        return service.create(id, SecurityContextHolder.getContext().getAuthentication().getName(),
                input == null ? 0 : input.hours());
    }

    @GetMapping
    public List<ReviewerAccessService.LinkSummary> list(@PathVariable String id) { return service.list(id); }

    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String id, @PathVariable long linkId) { service.revoke(id, linkId); }

    public record DurationRequest(int hours) {}
}
