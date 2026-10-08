package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.service.RorLookupService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scientific/ror")
public class RorLookupController {
    private final RorLookupService service;
    public RorLookupController(RorLookupService service) { this.service = service; }
    @GetMapping("/search")
    public List<RorLookupService.Organization> search(@RequestParam String q) { return service.search(q); }
}
