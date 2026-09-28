package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.repository.DoiRegistrationRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** Permanent, version-specific landing URL for DOI resolution. */
@Controller
public class PublicDatasetLandingController {
    private final DoiRegistrationRepository registrations;
    private final ScientificRecordRepository records;

    public PublicDatasetLandingController(DoiRegistrationRepository registrations, ScientificRecordRepository records) {
        this.registrations = registrations;
        this.records = records;
    }

    @GetMapping("/datasets/{id}")
    public String landing(@PathVariable String id) {
        return "forward:/public-resource.html";
    }

    @GetMapping("/datasets/{id}/concept")
    public String concept(@PathVariable String id) {
        String doi = registrations.findById("c:" + id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getDoi();
        var versions = records.findByConceptualDoiIgnoreCaseAndStatusOrderByPublishedAtDesc(doi, PublicationStatus.PUBLISHED);
        if (!versions.isEmpty()) return "redirect:/datasets/" + versions.get(0).getResourceId();
        return "redirect:/datasets/" + id;
    }
}
