package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.service.UsageMetricsService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class UsageMetricsController {
    private final UsageMetricsService usage;
    public UsageMetricsController(UsageMetricsService usage){this.usage=usage;}
    @GetMapping("/api/v1/public/usage")
    public ResponseEntity<UsageMetricsService.Report> summary(@RequestParam(required=false)String resourceId,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate until) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usage.report(from,until,resourceId,false));
    }
    @GetMapping("/api/v1/scientific/operations/usage")
    @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ResponseEntity<UsageMetricsService.Report> report(
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate until) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usage.report(from,until,null,true));
    }
}
